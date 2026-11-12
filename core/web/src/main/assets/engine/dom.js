(function () {
  var cfg = __CONFIG__;
  function text(el) { return ((el && (el.innerText || el.textContent)) || "").trim(); }
  var nodes = Array.prototype.slice.call(document.querySelectorAll(cfg.article || "article"));
  var tweets = nodes.map(function (article) {
    var nameBlock = text(article.querySelector(cfg.userName || "[data-testid='User-Name']"));
    var lines = nameBlock.split("\n").map(function (s) { return s.trim(); }).filter(Boolean);
    var handle = "";
    for (var i = 0; i < lines.length; i++) if (lines[i].charAt(0) === "@") handle = lines[i].substring(1);
    var link = article.querySelector("a[href*='/status/']");
    var href = link ? (link.getAttribute("href") || "") : "";
    var match = href.match(/status\/(\d+)/);
    var photos = Array.prototype.slice.call(article.querySelectorAll("img")).map(function (img) { return img.src || ""; })
      .filter(function (src) { return src.indexOf("https://") === 0 && src.indexOf("profile_images") === -1 && src.indexOf("emoji") === -1; })
      .slice(0, 4);
    var video = article.querySelector("video");
    var videoUrl = video ? (video.currentSrc || video.src || "") : "";
    return {
      id: match ? match[1] : "",
      text: text(article.querySelector(cfg.tweetText || "[data-testid='tweetText']")),
      name: lines[0] || "",
      handle: handle,
      time: lines.length ? lines[lines.length - 1] : "",
      photos: photos,
      videoUrl: videoUrl,
      poster: video ? (video.poster || "") : ""
    };
  }).filter(function (t) { return t.id || t.text; });
  return JSON.stringify({ tweets: tweets });
})();
