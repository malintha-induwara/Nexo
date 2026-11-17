package com.nexo.app.ui.tags

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexo.app.R
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.TextFieldDialog

@Composable
fun TagDialogs(dialog: TagDialog?, actions: TagActions) {
    val exists = stringResource(R.string.tag_exists)
    when (dialog) {
        is TagDialog.Add -> TextFieldDialog(
            stringResource(R.string.add_tag),
            "",
            maxLength = TagsRepository.MAX_LENGTH,
            error = exists.takeIf { dialog.nameTaken },
            filter = TagsRepository::filter,
            onConfirm = actions.add,
            onDismiss = actions.dismissDialog,
        )

        is TagDialog.Rename -> TextFieldDialog(
            stringResource(R.string.rename_tag),
            dialog.tag.name,
            maxLength = TagsRepository.MAX_LENGTH,
            error = exists.takeIf { dialog.nameTaken },
            filter = TagsRepository::filter,
            onConfirm = actions.rename,
            onDismiss = actions.dismissDialog,
        )

        is TagDialog.ConfirmDelete -> ConfirmDialog(
            stringResource(R.string.tag_in_use_delete),
            onConfirm = actions.confirmDelete,
            onDismiss = actions.dismissDialog,
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
        )

        null -> Unit
    }
}
