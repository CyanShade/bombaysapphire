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

  /**
   * ポータルのプロット。
  */
  function draw(data){
    var area = $("#plot_area");
    var w = area.width();
    var h = area.height();
    var g = document.getElementById("plot_area").getContext('2d');
    g.clearRect(0, 0, w, h);
    if(data.length === 0){ return; }
    g.fillStyle = 'rgba(119,184,218,0.7)';
    var x0 = data[0][1], x1 = data[0][1], y0 = data[0][0], y1 = data[0][0];
    $.each(data, function(){
      x0 = Math.min(x0, this[1]);
      x1 = Math.max(x1, this[1]);
      y0 = Math.min(y0, this[0]);
      y1 = Math.max(y1, this[0]);
    });
    var ox = 0, oy = 0, dx = 0, dy = 0;
    if(x0 == x1)  ox = w / 2;
    else          dx = w / (x1 - x0);
    if(y0 == y1)  oy = h / 2;
    else          dy = h / (y1 - y0);
    if(x0 != x1 && y0 != y1){
      dx = dy = Math.min(dx, dy);
      ox = (w - (x1 - x0) * dx) / 2;
      oy = (h - (y1 - y0) * dy) / 2;
    }
    var size = data.length > 1000? 1: 3;
    $.each(data, function(){
      var x = (this[1] - x0) * dx + ox, y = h - ((this[0] - y0) * dy + oy);
      g.fillRect(x, y, size, size);
      // console.log(JSON.stringify(this)+" => ("+x+","+y+"): ("+x0+","+y0+")-("+x1+","+y1+")");
    });
  }

  /**
   * ポータル情報の一覧表示。
  */
  function list(data){
    $("#portal_count").text(data.length);
    var table = $("#portals");
    table.empty();
    $.each(data, function(){
      table.append($("<tr/>")
        .append($("<td>").attr("style","text-align:right;").text(this.id))
        .append($("<td>")
          .append($("<img/>")
            .attr("src", this.image)
            .attr("style","height:64px;float:right;")
            .attr("title", this.title)
            .attr("alt", this.title)
          )
          .append($("<h4/>").append(this.title))
          .append($("<p/>")
            .append($("<a/>")
              .attr("href", "https://" + RemoteHost + "/intel?ll=" + this.latlng[0] + "," + this.latlng[1] + "&z=17")
              .attr("target", "intel")
              .attr("title", this.latlng[0] + "/" + this.latlng[1])
              .text(this.city + ", " + this.state + ", " + this.country)
            )
            .append($("<span/>")
              .attr("style", "font-size:0px;")  // コピペ時に現れる隠しテキストとして
              .text(" https://" + RemoteHost + "/intel?ll=" + this.latlng[0] + "," + this.latlng[1] + "&z=17")
            )
            .append($("<span/>")
              .attr("title", this.created_at)
              .text(" " + relativeDateTime(stringToDate(this.created_at)))
            )
          )
        )
      );
    });
  }

  /**
   * 検索ボタンが押された時のアクション。
  */
  $("#search").click(function(){
    var query = $("#search_form").serialize() + "&cll=" + latitude + "," + longitude;
    $.getJSON("/portal/locations?" + query, function(json){
      list(json);
      var latlng = [ ];
      $.each(json, function(){
        latlng.push(this.latlng);
      });
      draw(latlng);
    });
  });

  /**
   * geolocation を使用した位置情報の取得。
  */
  var latitude = NaN;
  var longitude = NaN;
  if(navigator.geolocation){
    navigator.geolocation.getCurrentPosition(function(position){
      latitude = position.coords.latitude;
      longitude = position.coords.longitude;
      var location = $("#current_location");
      var goecode = "http://maps.googleapis.com/maps/api/geocode/json?latlng=" + latitude + "," + longitude + "&sensor=false";
      var mapurl = "https://" + RemoteHost + "/intel?ll=" + latitude + "," + longitude + "&z=17";
      location.empty().append($("<a/>")
       .attr("href", mapurl)
       .attr("target", "intel")
       .append(latitude + "/" + longitude)
      );
      $.getJSON(goecode, function(json){
       if(json.status == "OK"){
         location.empty().append($("<a/>")
           .attr("href", mapurl)
           .attr("target", "intel")
           .attr("title", latitude + "/" + longitude)
           .append(json.results[0].formatted_address)
         );
       } else {
         location.append($("<span/>")
           .attr("class", "glyphicon glyphicon-exclamation-sign").attr("aria-hidden", "true")
           .attr("title", json.status + ": " + json.error_message)
         );
       }
      });
      });
  }

});
