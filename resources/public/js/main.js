// If it were more than just this one thing I'd use jQuery, but...

// Highlight the date specified in the URL fragment.
// For graceful degradation, I should do this server-side instead...
// ...but that'd probably mean query params, and I'd personally rather keep the
// URL clean. And *I'll* have js enabled, after all :)
var previouslyHighlighted = [];
function highlight() {
  for (var i = 0; i < previouslyHighlighted.length; i++) {
    previouslyHighlighted[i].className = previouslyHighlighted[i].className.replace('current','');
  }

  var hash = window.location.hash;
  if (hash) {
    var time = hash.substr(1);
    var entry = document.getElementById(time);
    if (entry) {
      var timeText = entry.getElementsByTagName('time')[0].textContent;
      do {
        entry.className += ' current';
        previouslyHighlighted.push(entry);
        entry = entry.nextSibling;
      } while (entry && entry.getElementsByTagName('time')[0].textContent == timeText);
    }
  }
}

window.onload = window.onhashchange = highlight;