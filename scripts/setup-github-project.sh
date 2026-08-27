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

ensure_option() {
  local name="$1"
  local existing
  existing=$(echo "$FIELD_JSON" | jq -r --arg n "$name" '.data.user.projectV2.field.options[] | select(.name==$n) | .id' | head -1)
  if [[ -n "$existing" && "$existing" != "null" ]]; then
    echo "$existing"
    return
  fi
  echo "Adding Status option: $name" >&2
  gh api graphql -f query='
    mutation($fieldId:ID!, $name:String!) {
      createProjectV2FieldOption(input:{
        fieldId:$fieldId
        name:$name
        dataType:SINGLE_SELECT
      }) {
        projectV2SingleSelectFieldOption { id name }
      }
    }' -f fieldId="$STATUS_FIELD_ID" -f name="$name" \
    --jq '.data.createProjectV2FieldOption.projectV2SingleSelectFieldOption.id' 2>/dev/null \
    || gh api graphql -f query='
      mutation($fieldId:ID!, $option:ProjectV2SingleSelectFieldOptionInput!) {
        updateProjectV2Field(input:{
          fieldId:$fieldId
          singleSelectOptions:[$option]
        }) {
          projectV2Field {
            ... on ProjectV2SingleSelectField { options { id name } }
          }
        }
      }' -f fieldId="$STATUS_FIELD_ID" -f option="$(jq -n --arg n "$name" '{name:$n,color:GRAY,description:\"\"}')" \
      --jq ".data.updateProjectV2Field.projectV2Field.options[] | select(.name==\"$name\") | .id"
}

# Refresh options after potential creates via updateProjectV2Field singleSelectOptions replace is risky.
# Prefer reading options; create missing ones with updateProjectV2Field by merging.
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
  # Build full options array for update — must include ALL existing + new
  MERGED=$(echo "$OPTIONS_JSON" | jq -c --argjson add "$(printf '%s\n' "${missing[@]}" | jq -R . | jq -s .)" '
    (.data.user.projectV2.field.options // []) as $cur
    | ($cur + ($add | map({name:., description:"", color:"GRAY"})))
  ')
  # Actually updateProjectV2Field replaces options and needs name+color+description for each;
  # existing ones need their id preserved when possible.
  PAYLOAD=$(echo "$OPTIONS_JSON" | jq -c --argjson add "$(printf '%s\n' "${missing[@]}" | jq -R . | jq -s .)" '
    def opt: {name:.name, description:(.description//""), color:(.color//"GRAY")};
    (.data.user.projectV2.field.options // [] | map(opt))
    + ($add | map({name:., description:"", color:"GRAY"}))
  ')
  gh api graphql -f query='
    mutation($fieldId:ID!, $options:[ProjectV2SingleSelectFieldOptionInput!]!) {
      updateProjectV2Field(input:{
        fieldId:$fieldId
        singleSelectOptions:$options
      }) {
        projectV2Field {
          ... on ProjectV2SingleSelectField {
            id
            options { id name }
          }
        }
      }
    }' -f fieldId="$STATUS_FIELD_ID" -f options="$PAYLOAD" >/tmp/keynest-status-update.json
  OPTIONS_JSON=$(cat /tmp/keynest-status-update.json | jq '{data:{user:{projectV2:{field:.data.updateProjectV2Field.projectV2Field}}}}')
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
