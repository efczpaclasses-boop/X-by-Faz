(function () {
  if (window.__xbfHook) return;
  window.__xbfHook = true;
  function send(url, body) {
    try {
      if (!body || body.length > 2000000) return;
      var u = String(url || "");
      if (u.indexOf("/dm/") !== -1 || u.indexOf("DirectMessage") !== -1) return;
      if (body.indexOf("tweet_results") === -1 && body.indexOf("full_text") === -1 && body.indexOf("\"screen_name\"") === -1) return;
      if (window.XByFazBridge && XByFazBridge.onTimeline) XByFazBridge.onTimeline(body);
    } catch (e) {}
  }
  if (window.fetch) {
    var orig = window.fetch;
    window.fetch = function () {
      var args = arguments;
      return orig.apply(this, args).then(function (res) {
        try {
          var url = (args[0] && args[0].url) || String(args[0] || "");
          res.clone().text().then(function (t) { send(url, t); }).catch(function () {});
        } catch (e) {}
        return res;
      });
    };
  }
  var open = XMLHttpRequest.prototype.open;
  var sendReq = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__xbfUrl = url;
    return open.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener("load", function () {
      try { send(this.__xbfUrl, this.responseText); } catch (e) {}
    });
    return sendReq.apply(this, arguments);
  };
})();
