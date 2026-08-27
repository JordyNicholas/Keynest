#!/usr/bin/env bash
# Create (or reuse) the Keynest GitHub Project V2 board and print workflow env IDs.
# Mirrors TokenForge / Flux Status columns + Epics Finished.
#
# Requires: PROJECT_TOKEN (or GH_TOKEN) with Projects:write for user JordyNicholas.
# Usage:
#   PROJECT_TOKEN=ghp_... ./scripts/setup-github-project.sh
#   PROJECT_TOKEN=ghp_... ./scripts/setup-github-project.sh --link-issues
set -euo pipefail

OWNER="${PROJECT_OWNER:-JordyNicholas}"
REPO="${PROJECT_REPO:-JordyNicholas/Keynest}"
TITLE="${PROJECT_TITLE:-Keynest — MVP Board}"
LINK_ISSUES=false
for arg in "$@"; do
  case "$arg" in
    --link-issues) LINK_ISSUES=true ;;
  esac
done

if [[ -n "${PROJECT_TOKEN:-}" ]]; then
  export GH_TOKEN="$PROJECT_TOKEN"
fi

if ! gh api graphql -f query='query { viewer { login } }' --jq '.data.viewer.login' >/dev/null 2>&1; then
  echo "ERROR: GitHub auth cannot call Projects GraphQL. Set PROJECT_TOKEN with Projects:write." >&2
  exit 1
fi

echo "Looking for existing project titled '$TITLE' under @$OWNER…"
EXISTING=$(gh api graphql -f query='
  query($login:String!) {
    user(login:$login) {
      projectsV2(first:50) {
        nodes { id number title url }
      }
    }
  }' -f login="$OWNER" \
  --jq ".data.user.projectsV2.nodes[] | select(.title==\"$TITLE\")" | head -1 || true)

if [[ -n "$EXISTING" ]]; then
  PROJECT_ID=$(echo "$EXISTING" | jq -r '.id')
  PROJECT_NUMBER=$(echo "$EXISTING" | jq -r '.number')
  PROJECT_URL=$(echo "$EXISTING" | jq -r '.url')
  echo "Reusing project #$PROJECT_NUMBER ($PROJECT_ID)"
else
  echo "Creating project…"
  CREATED=$(gh api graphql -f query='
    mutation($ownerId:ID!, $title:String!) {
      createProjectV2(input:{ownerId:$ownerId, title:$title}) {
        projectV2 { id number url }
      }
    }' -f ownerId="$(gh api graphql -f query='query($login:String!){ user(login:$login){ id } }' -f login="$OWNER" --jq '.data.user.id')" \
    -f title="$TITLE")
  PROJECT_ID=$(echo "$CREATED" | jq -r '.data.createProjectV2.projectV2.id')
  PROJECT_NUMBER=$(echo "$CREATED" | jq -r '.data.createProjectV2.projectV2.number')
  PROJECT_URL=$(echo "$CREATED" | jq -r '.data.createProjectV2.projectV2.url')
  echo "Created project #$PROJECT_NUMBER"
fi

# Ensure Status field options exist (Todo / In Progress / Ready for Review / Done / Epics Finished)
FIELD_JSON=$(gh api graphql -f query='
  query($owner:String!, $number:Int!) {
    user(login:$owner) {
      projectV2(number:$number) {
        field(name:"Status") {
          ... on ProjectV2SingleSelectField {
            id
            options { id name }
          }
        }
      }
    }
  }' -f owner="$OWNER" -F number="$PROJECT_NUMBER")

STATUS_FIELD_ID=$(echo "$FIELD_JSON" | jq -r '.data.user.projectV2.field.id')
echo "Status field: $STATUS_FIELD_ID"

# Read the Status field options; any missing ones are created below in one
# updateProjectV2Field call (the singleSelectOptions input replaces the set).
OPTIONS_JSON=$(gh api graphql -f query='
  query($owner:String!, $number:Int!) {
    user(login:$owner) {
      projectV2(number:$number) {
        field(name:"Status") {
          ... on ProjectV2SingleSelectField {
            id
            options { id name }
          }
        }
      }
    }
  }' -f owner="$OWNER" -F number="$PROJECT_NUMBER")

need_names=("Todo" "In Progress" "Ready for Review" "Done" "Epics Finished")
# GitHub default often uses "Todo" already; Flux uses "Todo", TokenForge "To-Do" in docs but IDs say Todo.
missing=()
for n in "${need_names[@]}"; do
  id=$(echo "$OPTIONS_JSON" | jq -r --arg n "$n" '.data.user.projectV2.field.options[] | select(.name==$n) | .id' | head -1)
  if [[ -z "$id" || "$id" == "null" ]]; then
    # Accept "To-Do" as Todo
    if [[ "$n" == "Todo" ]]; then
      id=$(echo "$OPTIONS_JSON" | jq -r '.data.user.projectV2.field.options[] | select(.name=="To-Do" or .name=="Todo") | .id' | head -1)
    fi
  fi
  if [[ -z "$id" || "$id" == "null" ]]; then
    missing+=("$n")
  fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Missing Status options: ${missing[*]}"
  echo "Building merged option list…"
  # updateProjectV2Field.singleSelectOptions REPLACES the whole option set and
  # its input type (ProjectV2SingleSelectFieldOptionInput) has no id field, so we
  # must resend every option (existing + missing). New IDs are assigned; this is
  # safe only on a fresh/empty board, which is why this runs before --link-issues.
  #
  # gh's -f only binds String variables, so a [ProjectV2SingleSelectFieldOptionInput!]!
  # variable must be sent as real JSON. Post the mutation to the GraphQL endpoint
  # directly with a jq-built {query, variables} body.
  color_for() {
    case "$1" in
      "In Progress") echo "BLUE" ;;
      "Ready for Review") echo "PURPLE" ;;
      "Done") echo "GREEN" ;;
      "Epics Finished") echo "PINK" ;;
      *) echo "GRAY" ;;
    esac
  }
  OPTIONS_INPUT=$(echo "$OPTIONS_JSON" | jq -c '[.data.user.projectV2.field.options[]?.name]')
  for n in "${missing[@]}"; do
    OPTIONS_INPUT=$(echo "$OPTIONS_INPUT" | jq -c --arg n "$n" '. + [$n]')
  done
  # Turn the ordered name list into full option objects with a stable color map.
  PAYLOAD='[]'
  while IFS= read -r n; do
    [[ -z "$n" ]] && continue
    PAYLOAD=$(echo "$PAYLOAD" | jq -c --arg n "$n" --arg c "$(color_for "$n")" \
      '. + [{name:$n, color:$c, description:""}]')
  done < <(echo "$OPTIONS_INPUT" | jq -r '.[]')

  VARS=$(jq -cn --arg fieldId "$STATUS_FIELD_ID" --argjson options "$PAYLOAD" \
    '{fieldId:$fieldId, options:$options}')
  BODY=$(jq -cn --arg q 'mutation($fieldId:ID!, $options:[ProjectV2SingleSelectFieldOptionInput!]!) {
      updateProjectV2Field(input:{fieldId:$fieldId, singleSelectOptions:$options}) {
        projectV2Field { ... on ProjectV2SingleSelectField { id options { id name } } }
      }
    }' --argjson v "$VARS" '{query:$q, variables:$v}')
  RESP=$(curl -sS -H "Authorization: Bearer ${GH_TOKEN}" -H "Content-Type: application/json" \
    -d "$BODY" https://api.github.com/graphql)
  if echo "$RESP" | jq -e '.errors' >/dev/null 2>&1; then
    echo "ERROR: failed to set Status options:" >&2
    echo "$RESP" | jq -r '.errors' >&2
    exit 1
  fi
  OPTIONS_JSON=$(echo "$RESP" | jq '{data:{user:{projectV2:{field:.data.updateProjectV2Field.projectV2Field}}}}')
fi

opt_id() {
  local name="$1"
  echo "$OPTIONS_JSON" | jq -r --arg n "$name" '
    .data.user.projectV2.field.options[]
    | select(.name==$n or ($n=="Todo" and (.name=="To-Do" or .name=="Todo")))
    | .id' | head -1
}

STATUS_TODO=$(opt_id "Todo")
STATUS_IN_PROGRESS=$(opt_id "In Progress")
STATUS_READY=$(opt_id "Ready for Review")
STATUS_DONE=$(opt_id "Done")
STATUS_EPICS=$(opt_id "Epics Finished")

echo
echo "=== Paste into .github/workflows/project-*-lifecycle.yml env: ==="
cat <<EOF
  PROJECT_OWNER: $OWNER
  PROJECT_NUMBER: '$PROJECT_NUMBER'
  PROJECT_ID: $PROJECT_ID
  STATUS_FIELD_ID: $STATUS_FIELD_ID
  STATUS_TODO: $STATUS_TODO
  STATUS_IN_PROGRESS: $STATUS_IN_PROGRESS
  STATUS_READY_FOR_REVIEW: $STATUS_READY
  STATUS_DONE: $STATUS_DONE
  STATUS_EPICS_FINISHED: $STATUS_EPICS
EOF
echo
echo "Project URL: $PROJECT_URL"

if [[ "$LINK_ISSUES" == "true" ]]; then
  echo "Linking open + closed issues from $REPO onto the project…"
  mapfile -t NUMS < <(gh issue list -R "$REPO" --state all --limit 200 --json number --jq '.[].number' | sort -n)
  for n in "${NUMS[@]}"; do
    ISSUE_ID=$(gh api graphql -f query='
      query($owner:String!, $name:String!, $number:Int!) {
        repository(owner:$owner, name:$name) { issue(number:$number) { id title } }
      }' -f owner="${REPO%/*}" -f name="${REPO#*/}" -F number="$n" \
      --jq '.data.repository.issue.id')
    TITLE_I=$(gh issue view "$n" -R "$REPO" --json title --jq '.title')
    ITEM=$(gh api graphql -f query='
      mutation($projectId:ID!, $contentId:ID!) {
        addProjectV2ItemById(input:{projectId:$projectId, contentId:$contentId}) {
          item { id }
        }
      }' -f projectId="$PROJECT_ID" -f contentId="$ISSUE_ID" \
      --jq '.data.addProjectV2ItemById.item.id' 2>/dev/null || true)
    if [[ -z "$ITEM" ]]; then
      echo "  #$n already on project or failed"
      continue
    fi
    # Default Todo; closed stories → Done; closed epics → Epics Finished
    OPTION="$STATUS_TODO"
    STATE=$(gh issue view "$n" -R "$REPO" --json state --jq '.state')
    if [[ "$STATE" == "CLOSED" ]]; then
      if [[ "$TITLE_I" =~ ^\[Epic\] ]]; then
        OPTION="$STATUS_EPICS"
      else
        OPTION="$STATUS_DONE"
      fi
    fi
    if [[ -n "$OPTION" && "$OPTION" != "null" ]]; then
      gh api graphql -f query='
        mutation($projectId:ID!, $itemId:ID!, $fieldId:ID!, $optionId:String!) {
          updateProjectV2ItemFieldValue(input:{
            projectId:$projectId
            itemId:$itemId
            fieldId:$fieldId
            value:{ singleSelectOptionId:$optionId }
          }) { projectV2Item { id } }
        }' \
        -f projectId="$PROJECT_ID" \
        -f itemId="$ITEM" \
        -f fieldId="$STATUS_FIELD_ID" \
        -f optionId="$OPTION" --silent
    fi
    echo "  linked #$n"
  done
fi

echo "Done."
