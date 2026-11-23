# Password tools and inline diagnostics

Implementation follow-up for #221, subject to feature PR review and the ongoing Windows/Edge walkthrough in #199.

The canonical Password tools page is `/tools`. Material accordion choices expose generation or policy checking on demand. Only the selected tool is mounted: opening one closes the other, and closing/switching destroys its component, clears temporary generated/sample password state and cancels pending policy/validation requests. Tool choice is represented in the URL for direct links; passwords and keywords are never put into navigation parameters. Workspace tools require sign-in. Anonymous generation is on the landing page; see [the route map](WEB_ROUTES.md). Existing server-side account-policy enforcement is unchanged.

Use `/tools?tool=generate#generate` or `/tools?tool=check#check` for direct tool links. Obsolete standalone paths are removed. AI setup still lives under Account settings, and the returned explanation uses transient navigation info, never provider credentials, URLs or history state. Core tools continue to work with AI disabled.

API health is an on-demand inline check under Account settings → Troubleshooting. Its response is rendered as connection status/service/default policy rather than raw JSON. Merely visiting Account settings does not start a health request. Closing the check cancels work. There is no separate health page or Back navigation step.

Section navigation uses smooth scrolling only when reduced motion is not requested. Material headers retain keyboard expansion/collapse and visible selected state. The unopened workflows do not make background requests.

Verification covers anonymous redirects, canonical URLs, selected workflows, secret clearing/request cancellation on close/switch, transient AI handoff, inline health success/failure/retry and cancellation. Actual motion, scrolling, focus and narrow-screen readability remain part of the human Windows/Edge walkthrough; automated tests alone do not close it.
