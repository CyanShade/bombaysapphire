var ruli;

$(function(){

  // #####################################
  // パネルの切り替え

  (function(){
    var panels = [ "events", "portal", "new_portal" ];
    $.each(panels, function(i, value){
      $("#menu_" + value).click(function(){
        $.each(panels, function(j, value2){ $("#main_" + value2).hide(); });
        $("#main_" + value).show();
      });
    });
  })();

  // #####################################

  function decode(s){
    var t = "";
    for(var i=0; i<s.length; i++){
      t += String.fromCharCode(s.charCodeAt(i) + 3);
    }
    return t;
  }
  var RemoteHost = decode("ttt+fkdobpp+`lj");

  /**
   * 現在時刻からの相対時間に変換。
  */
  function relativeDateTime(dt){
    var sec = (new Date().getTime() - dt.getTime()) / 1000;
    if(sec < 5)  return "今";
    if(sec < 60)  return (Math.floor(sec/5)*5) + "秒前";
    if(sec < 60 * 60)  return Math.floor(sec/60) + "分前";
    if(sec < 24 * 60 * 60)  return Math.floor(sec/60/60) + "時間前";
    return Math.floor(sec/24/60/60) + "日前";
  }

  /**
  */
  function stringToDate(str){
    str.match(/(\d+)\/(\d+)\/(\d+) (\d+):(\d+)/);
    var year = parseInt(RegExp.$1);
    var month = parseInt(RegExp.$2);
    var date = parseInt(RegExp.$3);
    var hour = parseInt(RegExp.$4);
    var minute = parseInt(RegExp.$5);
    return new Date(year, month - 1, date, hour, minute);
  }

  /** リンク付きの住所情報要素を構築。 */
  function create_address_link(country, state, city, lat, lng){
    return create_portal_link(city + ", " + state + ", " + country, lat, lng);
  }

  /** リンク付きの住所情報要素を構築。 */
  function create_portal_link(label, lat, lng){
    return $("<a/>")
      .attr("href", "https://" + RemoteHost + "/intel?pll=" + lat + "," + lng + "&z=17")
      .attr("target", "intel")
      .attr("title", lat + "/" + lng)
      .text(label);
  }

  /** リンク付きの住所情報要素を構築。 */
  function create_pinned_link(label, pinLabel, lat, lng){
    return $("<a/>")
      .attr("href", "https://" + RemoteHost + "/intel?ll=" + lat + "," + lng + "&z=17&q=" + encodeURIComponent(pinLabel))
      .attr("target", "intel")
      .attr("title", lat + "/" + lng)
      .text(label);
  }

  /** 時刻要素を構築。 */
  function create_timestamp(tm){
    return $("<span/>")
      .attr("title", tm)
      .text(relativeDateTime(stringToDate(tm)));
  }

  /** 画像要素を構築。 */
  function create_image(title, src, height){
    return $("<a/>")
      .attr("href", src)
      .append($("<img/>")
        .attr("src", src)
        .attr("title", title)
        .attr("border", "0")
        .attr("style", (height===null)? "": "height:" + height + ";"));
  }

  ruli = {
    remoteHost: RemoteHost,
    createAddressLink: create_address_link,
    createPortalLink: create_portal_link,
    createPinnedLink: create_pinned_link,
    createTimestamp: create_timestamp,
    createImage: create_image,
  };

});