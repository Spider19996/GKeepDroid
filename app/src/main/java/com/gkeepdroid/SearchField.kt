package com.gkeepdroid

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource

/**
 * The Material 3 search bar. It stays a bar (never expands to its own screen), because the lists below
 * are filtered while typing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) ClearButton(onClick = { onQueryChange("") }) },
                modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier,
        windowInsets = WindowInsets(0),
        content = {},
    )
}
