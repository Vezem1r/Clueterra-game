(function () {
  try {
    var root = document.documentElement
    if (window.localStorage.getItem('clu_auth_session') === '1') {
      root.setAttribute('data-auth-session', '')
    }
    if (window.localStorage.getItem('clueterra_cookieConsent') === '1') {
      root.setAttribute('data-cookie-consent', '')
    }
    var hint = JSON.parse(window.localStorage.getItem('clueterra:today-state') || 'null')
    if (!hint || hint.date !== new Date().toISOString().slice(0, 10)) return
    if (hint.clueterra) root.setAttribute('data-today-clueterra', hint.clueterra)
    if (hint.budget) root.setAttribute('data-today-budget', hint.budget)
  } catch (error) {
    return
  }
})()
