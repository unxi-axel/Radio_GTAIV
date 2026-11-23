private fun login() {
    val req = AuthorizationRequest.Builder(
        RadioViewModel.CLIENT_ID,
        AuthorizationResponse.Type.TOKEN,
        RadioViewModel.REDIRECT_URI
    )
        .setScopes(arrayOf("playlist-read-private", "playlist-read-collaborative"))
        .setShowDialog(true)
        .build()
    AuthorizationClient.openLoginInBrowser(this, req)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val uri = intent.data ?: return
    val resp = AuthorizationResponse.fromUri(uri)
    if (resp.type == AuthorizationResponse.Type.TOKEN) vm.onToken(resp.accessToken)
    else vm.status.value = "Connexion refusee : ${resp.error ?: resp.type}"
}
