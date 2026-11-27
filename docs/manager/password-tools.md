# Password tools

## Public homepage

Open **Generate a password** or **Check a password** on the homepage. Generation creates 20-character passwords under the built-in rules using Web Crypto. **Copy** writes the generated value to the clipboard only when requested. The checker evaluates a masked sample locally and indicates checks it cannot confirm. **Show password** and **Hide password** toggle visibility. Samples and generated values stay in the browser, and closing or switching tools clears them. Direct links use `/#generator` and `/#check`.

## Signed-in tools

Open **Tools** in the workspace. **Generate password** lets you adjust length, permitted character types, and an optional keyword within your account policy. **Check password policy** previews rules as you type and can send a sample to the Manager API for a final check. Closing or switching clears temporary values. Direct links use `/tools?tool=generate#generate` and `/tools?tool=check#check`.

For personalized rules, open **Account settings → Password policy** and select the default or save custom rules. The selection applies to signed-in generation and checking, new Vault items, and replacements. The server also checks rules that depend on a saved username when you save a Vault item. See [policy rules and API behavior](../api/account-password-policy.md).

## Optional AI explanation

If the operator enables the feature, choose **Explain the built-in policy with AI** from the checker or open **Account settings → AI settings → OpenAI setup**. Supply your own provider key and request the explanation. The provider receives the built-in policy and the request uses the key for authentication. Read [configuration, data handling, and verification limits](../api/policy-explanations.md).

## Check the API connection

Open **Account settings → Troubleshooting → Check API connection**. **Check again** repeats the request. The result shows whether this web app can reach the configured Manager API. Operators can check [database readiness](../operators/runtime.md) separately.
