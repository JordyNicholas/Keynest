# Back up and restore Vault items

Export creates a passphrase-protected `.keynest` file that a compatible Keynest account can restore. For complete instance recovery, retain a [database backup and its matching server vault key](../operators/database-migrations.md). Store the export passphrase separately from the file. The passphrase cannot be reset.

## Export

1. In **Vault**, select any items you want to export using the labelled checkbox on each card badge. Selection persists across pages, search, and filters, shows a count, and supports up to 500 items. **Clear export selection** resets it.
2. Open **Export**, choose **All items** or **Selected items**, then select **Review export**. All items means your whole Vault, including items hidden by filters. If the Vault exceeds 500 items, export selected subsets.
3. Enter your current account password and a separate export passphrase twice. Use 15 or more Unicode code points, up to 1,024 UTF-16 units.
4. Select **Verify and download encrypted file**. Confirm your browser saved `keynest-vault.keynest`.

Review checks selection without revealing passwords. Opening the dialog conceals any revealed password. The API rechecks ownership, scope, and the snapshot before download. Credential inputs clear on submit, cancellation, tab hide, or session change. See the [export API](../api/vault-export.md) for request and error details.

## Using a `.keynest` file

1. Sign in to the receiving Keynest account. Use matching versions of Manager Web and API on that instance.
2. Open **Vault → Restore** and choose your `.keynest` file. Enter the passphrase chosen during export and select **Review encrypted file**.
3. Review the add, update, and ignore counts. Identical entries and changed matches default to **Ignore**. Choose **Keep both** to add another item or **Update existing** to replace a changed match. Choose a receiving item if more than one matches. Select **Review selected actions** again after changing a choice.
4. For a review that will write items, enter the receiving account's current password. Acknowledge replacement if you selected updates, then select **Verify and apply selected actions**. A review with everything ignored requires no confirmation.
5. Check the refreshed Vault. Current filters still apply. Verify a restored item before relying on the file. If the outcome is uncertain, close the dialog and refresh Vault before trying again.

## Workflow and privacy contract

The file is authenticated and encrypted with its export passphrase. Restore uses the receiving instance's Vault key for newly saved passwords. Review writes no items and displays match labels, dates, and changed-field names while keeping passwords hidden. Changes to the file, passphrase, or decisions require fresh review. Apply binds the reviewed actions to the receiving account, session, file, and receiving state. The selected writes commit together. Updated items keep their ID and creation date.

The browser sends the file and required credentials to the configured authenticated API without sending the local filename. Closing the dialog, tab hide, session change, and review expiry clear the applicable inputs and review state. A canceled browser request may have reached the server.

See the [format and interoperability reference](../api/vault-portability.md), [restore decisions](../architecture/decisions/ADR-0016-restore-conflict-resolution.md), and [recorded browser evidence](../archive/mvp-release-evidence.md). `.keynest` is the supported native format. Third-party migration is still deferred.
