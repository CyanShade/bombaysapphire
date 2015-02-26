$(function(){
  var currentPortals = [];    // 現在表示中のポータル

  /** ポータル情報の一覧表示 */
  function list(data){
    $("#portal_count").text(ruli.displayNumber(data.length));
    var table = $("#portals");
    table.empty();
    var center = gmap.center();
    var from = new google.maps.LatLng(center[0], center[1]);
    $.each(data, function(){
      var to = new google.maps.LatLng(this.latlng[0], this.latlng[1]);
      var distance = google.maps.geometry.spherical.computeDistanceBetween(from, to);
      var id = this.id;
      var warn = $("<small/>").attr("class", "text-warning").text(" ");
      var guardian = this.team=='E'? Math.floor(this.guardian/24/60/60/1000): 0;
      table.append($("<tr/>")
        // .append($("<td>").attr("style","text-align:right;").text(this.id))
        .append($("<td>")
          .append($("<img/>")
            .attr("src", this.image)
            .attr("style","max-width:64px;max-height:64px;float:right;")
            .attr("title", this.title)
            .attr("alt", this.title)
          )
          .append($("<h5/>")
            .append($("<a/>").attr("href", "#")
              .append($("<span class='glyphicon glyphicon-map-marker' aria-hidden='true'></span>"))
              .click(function(){
                gmap.focus(id);
                return false;
              }))
            .append(" ")
            .append($("<a/>")
              .attr("href", "#")
              .attr("data-toggle", "modal")
              .attr("data-target", "#portal_detail")
              .attr("data-id", id)
              .attr("data-title", this.title)
              .text(this.title)))
          .append($("<p/>")
            .attr("style", "font-size:smaller;")
            .append(ruli.createAddressLink(this.country, this.state, this.city, this.latlng[0], this.latlng[1]))
            .append($("<span/>")
              .attr("style", "font-size:0px;")  // コピペ時に現れる隠しテキストとして
              .text(" https://" + ruli.remoteHost + "/intel?pll=" + this.latlng[0] + "," + this.latlng[1] + "&z=17")
            )
            .append(" ")
            .append(ruli.displayNumber(distance))
            .append("m ")
            .append(ruli.createTimestamp(this.created_at))
          )
          .append($("<div/>")
            .append($("<span/>")
              .attr("class", "label label-" + (this.team=="E"? "success": (this.team=="R"? "info":"default")))
              .text(ruli.millisToDisplayTimeSpan(this.guardian))
            ))
        )
      );
    });
  }

  /** ポータル詳細が開いたときにデータを設定する */
  $('#portal_detail').on('show.bs.modal', function(event){
    var link = $(event.relatedTarget);
    var id = link.data("id");
    var title = link.data("title");
    var modal = $(this);
    modal.find('.modal-title').text(title);
    $.getJSON("/api/1.0/portal/" + id, function(json){
      modal.find("img.portal_detail_image").attr("src", json.image);
      modal.find("a.portal_detail_image").attr("href", json.image);
      modal.find(".portal_detail_latlng").empty().append($("<a/>")
        .attr("href", "https://" + ruli.remoteHost + "/intel?pll=" + json.latlng[0] + "," + json.latlng[1] + "&z=17")
        .attr("target", "intel")
        .text(json.latlng[0] + "/" + json.latlng[1]));
      modal.find(".portal_detail_created_at").text(json.created_at);
      modal.find(".portal_detail_verified_at").text(json.verified_at);
      modal.find(".portal_detail_deleted_at").text(json.deleted_at === null? "-": json.deleted_at);
      getAddress(json.latlng[0], json.latlng[1], function(lat,lng,addr){
        modal.find(".portal_detail_address").text(addr);
      }, function(lat,lng,msg){
        modal.find(".portal_detail_address").empty().append($("<i/>").attr("title",msg).text("(不明)"));
      });
      modal.find(".portal_detail_guardian").text(json.team + ": " + ruli.millisToDisplayTimeSpan(json.guardian));
      // ポータルイベントの詳細を表示
      var events = $("#portal_detail_event_history");
      events.empty();
      $.each(json.event_log, function(){
        var elem = $("<span/>");
        switch(this.action){
          case "create":
            elem.text("ポータルを検出しました.");
            break;
          case "portal_removed":
            elem.text("ポータルの削除を検出しました.");
            break;
          case "change_image":
            elem.append($("<img/>").attr("src", this.old_value).attr("style", "float:right;max-width:32px;max-height:32px;"))
              .append("画像が変更されました.");
            break;
          case "change_title":
            elem.append("ポータル名が " + this.old_value + " から ")
              .append($("<b/>").append(this.new_value))
              .append(" に変更されました.");
            break;
          case "change_location":
            var l0 = toLatLng(this.old_value);
            var l1 = toLatLng(this.new_value);
            var d = ruli.gmap.distance(l0, l1);
            var h = ruli.gmap.heading(l0, l1);
            var dh = ruli.gmap.displayHeading(h);
            elem
              .append($("<a/>").attr("href", ruli.gmap.pinnedGMapUrl(json.title + "(旧)", l0)).attr("target", "_blank")
                .append($("<span/>")
                  .attr("class", "glyphicon glyphicon-map-marker").attr("aria-hidden", "true")
                  .attr("title", this.old_value)))
              .append("から")
              .append($("<span/>").attr("title", Math.round(h)+"°").text(dh))
              .append("に ")
              .append($("<span/>").attr("title",this.old_value).text(Math.round(d)+"m"))
              .append(" 移動しました.");
            break;
          default:
            elem.append(this.action + ": " + this.old_value + " -> " + this.new_value);
            break;
        }
        events.append($("<tr/>")
          .append($("<td/>").append(elem))
          .append($("<td/>").text(this.created_at))
        );
      });
      // ポータル占拠状態をグラフ表示
      /*
      var team = [ "" ];
      var tm = [ "時刻"];
      var level = [ "Level" ];
      $.each(json.state_log, function(){
        team.push(this.team);
        tm.push(this.created_at);
        level.push(this.level);
      });
      var capture = {
        "config": {
          "type": "scatter",
          "useMarker": "css-ring",
          "xLines": "none",
          "xScaleSkip": 0,
          "maxWsColLen": 120,
          "maxY": 8,
          "minY": -8,
          "shadows": {"hanrei":["#333",5,5,5]},
          "colorSet":
                ["rgba(153,255,255,0.8)","rgba(153,255,153,0.8)"],
          "bgGradient": {
                  "direction":"vertical",
                  "from":"#111",
                  "to":"#798589"
                }
        },
        "data": [ team, tm, level ]
      };
      ccchart.init('portal_detail_graph_capture', capture);
      */
    });
  });

  /** 文字列の緯度,経度を LatLng に変換 */
  function toLatLng(s){
    var ll = $.map(s.split(","), function(n,i){ return parseFloat(n); });
    return new google.maps.LatLng(ll[0], ll[1]);
  }

  /**
   * 検索の実行。
  */
  function search(){
    var c = gmap.center();
    var query = $("#search_form").serialize() + "&cll=" + c[0] + "," + c[1];
    $.getJSON("/api/1.0/portals.json?" + query, function(json){
      currentPortals = json;
      list(json);
      gmap.update(json);
    });
  }

  /** 現在表示しているポータル一覧からスキャナ範囲に含まれるポータル数を多い順に検索。 */
  function scanMassiveLocation(){
    var portals = currentPortals;
    if(portals.length === 0){
      return;
    }
    var button = $("#scanfarm");
    button.attr("disabled", "disabled");
    // スキャン範囲を決定
    var lat0 = portals[0].latlng[0], lat1 = portals[0].latlng[0];
    var lng0 = portals[0].latlng[1], lng1 = portals[0].latlng[1];
    $.each(portals, function(){
      lat0 = Math.min(lat0, this.latlng[0]);
      lng0 = Math.min(lng0, this.latlng[1]);
      lat1 = Math.max(lat1, this.latlng[0]);
      lng1 = Math.max(lng1, this.latlng[1]);
    });
    // 観測点の決定
    var origin = google.maps.geometry.spherical.computeOffset(new google.maps.LatLng(lat1, lng0), 80 * Math.sqrt(2), -45);
    var terminal = google.maps.geometry.spherical.computeOffset(new google.maps.LatLng(lat0, lng1), 80 * Math.sqrt(2), 90+45);
    var distance = 10;
    var point = origin;
    var locations = [];
    while(point.lat() >= terminal.lat()){
      locations.push(point);
      point = google.maps.geometry.spherical.computeOffset(point, distance, 90);
      if(point.lng() > terminal.lng()){
        point = new google.maps.LatLng(google.maps.geometry.spherical.computeOffset(point, distance, 180).lat(), origin.lng());
      }
    }

    // スキャナの位置を移動しながら各ポータルの包含判定
    var progress = $("#scanfarm_progress");
    var scanner = new google.maps.Circle({    // アニメーション用のスキャナ
      fillColor: "crimson",
      strokeColor: "crimson",
      radius: 40,
      map: gmap.map(),
    });
    // ブラウザの JavaScript では非同期処理が行えないためタイマーを使って実行
    function _evaluate(i){
      var location = locations[i];
      scanner.setCenter(location);
      // 各ポータルとの距離を計算
      var r = new Array(0, 0, 0);   // 40m, 60m, 80m
      $.each(portals, function(){
        var position = new google.maps.LatLng(this.latlng[0], this.latlng[1]);
        var d = google.maps.geometry.spherical.computeDistanceBetween(location, position);
        if(d <= 40) r[0] ++;
        if(d <= 60) r[1] ++;
        if(d <= 80) r[2] ++;
        // console.log("("+location.lat()+","+location.lng()+")->("+position.lat()+","+position.lng()+"): "+d+"m");
      });
      var opacity = Math.min((r[0] + (r[1]-r[0])*(4/6) + (r[2]-r[1])*(4/8)) / 10, 1.0) * 0.8;
      console.log("("+location.lat()+","+location.lng()+"): r40="+r[0]+",r60="+r[1]+",r80="+r[2]+"; opacity="+opacity);
      if(true){
        var n = google.maps.geometry.spherical.computeOffset(location, distance/2, 0).lat();
        var s = google.maps.geometry.spherical.computeOffset(location, distance/2, 180).lat();
        var e = google.maps.geometry.spherical.computeOffset(location, distance/2, 90).lng();
        var w = google.maps.geometry.spherical.computeOffset(location, distance/2, -90).lng();
        new google.maps.Rectangle({
          bounds: new google.maps.LatLngBounds(new google.maps.LatLng(s, w), new google.maps.LatLng(n, e)),
          map: gmap.map(),
          strokeColor: '#FF0000',
          strokeOpacity: 0.0,
          strokeWeight: 0,
          fillColor: '#FF0000',
          fillOpacity: opacity
        });
      }
      // 次の観測点へ移動するか処理を終了
      if(i + 1 < locations.length){
        progress.text(" (" + Math.floor(i / locations.length * 100) + "%)");
        setTimeout(function(){ _evaluate(i+1); }, 20);
      } else {
        progress.text("");
        scanner.setMap(null);
        button.removeAttr("disabled");
        alert("ok, " + portals.length + " portals, " + locations.length + " locations");
      }
    }
    _evaluate(0);
  }

  /**
   * 検索ボタンが押された時のアクション。
  */
  $("#search").click(search);

  /** ダウンロードボタンが押された時のアクション */
  $("#download").click(function(){
    var c = gmap.center();
    var query = $("#search_form").serialize() + "&cll=" + c[0] + "," + c[1] + "&dl=on&limit=1000";
    window.location = "/api/1.0/portals.kml?" + query;
  });

  $("#mapsync").click(function(){
    gmap.mapSync(! $("#mapsync").hasClass("active"));
  });

  $("#scanfarm").click(scanMassiveLocation);

  $("#hideportals").click(function(){
    var elem = $("#hideportals");
    var hidden = $.data(elem, "hidden");
    gmap.showPortals(!hidden);
    elem.toggleClass("active", hidden);
    $.data(elem, "hidden", !hidden);
  });

  var gmap = null;
  (function(){
    var map = null;
    var portalMarkers = {};
    var centerMarkers = [];
    var infowindow = new google.maps.InfoWindow({ });
    function resetCenterMarkers(){
      $.each(centerMarkers, function(){
        this.setCenter(map.getCenter());
      });
    }
    function updateMapBounds(){
      var bounds = map.getBounds();
      var ne = bounds.getNorthEast();
      var sw = bounds.getSouthWest();
      $("#bounds").attr("value", ne.lat() + "," + ne.lng() + "," + sw.lat() + "," + sw.lng());
      // 中央位置を更新
      var center = map.getCenter();
      centerAddress.set(center.lat() + "/" + center.lng(), "", center.lat(), center.lng());
      resetCenterMarkers();
    }
    function mapMoving(){
      resetCenterMarkers();
    }
    function mapChanged(){
      if($("#mapsync").hasClass("active")){
        updateMapBounds();
        search();
      }
    }
    function deleteAllPortals(){
      gmap.showPortals(false);
      portalMarkers = {};
    }

    gmap = {
      map: function(){ return map; },
      initialize: function(selector, latitude, longitude){
        if(map === null){
          var mapOptions = {
            zoom: 15,
            center: new google.maps.LatLng(latitude, longitude)
          };
          map = new google.maps.Map($(selector).get()[0], mapOptions);
          google.maps.event.addListener(map, "center_changed", mapMoving);
          google.maps.event.addListener(map, "dragend", mapChanged);
          google.maps.event.addListener(map, "zoom_changed", mapChanged);
          google.maps.event.addListener(map, "tilesloaded", mapChanged);
          setTimeout(0, function(){
            updateMapBounds();
            search();
          });
          // 中央をマーク
          centerMarkers.push(new google.maps.Circle({
            radius: 1,
            strokeColor: "#191970",   // Midnight Blue
            fillColor: "#191970",
            map: map
          }));
          centerMarkers.push(new google.maps.Circle({
            radius: 40,
            strokeColor: "#36479F",   // Blu Michelangelo
            fillColor: "#36479F",
            map: map
          }));
          centerMarkers.push(new google.maps.Circle({
            radius: 60,
            strokeColor: "#36479F",   // Blu Michelangelo
            strokeOpacity: 0.0,
            fillColor: "#36479F",
            fillOpacity: 0.3,
            map: map
          }));
        }
      },
      /** 表示領域変更によるポータルの更新 */
      update: function(json){
        deleteAllPortals();
        // 新しいマーカーを設定
        $.each(json, function(){
          var markerOptions = {
            map: map,
            title: this.title,
            opacity: 0.8,
            position: new google.maps.LatLng(this.latlng[0], this.latlng[1])
          };
          var marker = new google.maps.Marker(markerOptions);
          var title = this.title;
          var img = this.image;
          var lat = this.latlng[0], lng = this.latlng[1];
          var addr = this.city + ", " + this.state + ", " + this.country;
          var link = "https://" + ruli.remoteHost + "/intel?pll=" + lat + "," + lng + "&z=17";
          google.maps.event.addListener(marker, 'click', function() {
            infowindow.setContent(
              "<div>" +
              "<img src='" + img + "' style='max-width:120px;max-height:120px;'/><br/>" +
              "<b style='text-shadow:none;color:black;'>" + title + "</b><br/>" +
              "<small><a href='" + link + "' target='intel' style='color:black;'>" + addr + "</a></small>" +
              "</div>");
            infowindow.open(map,marker);
          });
          portalMarkers[this.id] = marker;
        });
      },
      /** 地図同期の設定 */
      mapSync: function(on){
        var mapsync = $("#mapsync");
        var fit = $("#fit");
        mapsync.toggleClass("active", on);
        if(on){
          fit.attr("disabled", "disabled");
          updateMapBounds();
        } else {
          fit.removeAttr("disabled");
          $("#bounds").attr("value", "");
        }
        search();
      },
      /** ポータルの表示/非表示切り替え */
      showPortals: function(show){
        for(var pid in portalMarkers){
          portalMarkers[pid].setMap(show? map: null);
        }
      },
      /** センターポジションの参照 */
      center: function(){
        var c = map.getCenter();
        return [ c.lat(), c.lng() ];
      },
      /** ポータルのフォーカス */
      focus: function(pid){
        for(var p in portalMarkers){
          portalMarkers[p].setAnimation(null);
        }
        var portal = portalMarkers[pid];
        if(portal !== null){
          map.setCenter(portal.getPosition());
          portal.setAnimation(google.maps.Animation.BOUNCE);
          setTimeout(function(){ portal.setAnimation(null); }, 3000);
        }
      }
    };
  })();

  /** 緯度/経度から行政区を取得しコールバックする */
  function getAddress(lat, lng, success, failure){
    var goecode = "http://maps.googleapis.com/maps/api/geocode/json?latlng=" + lat + "," + lng + "&sensor=false";
    $.getJSON(goecode, function(json){
      if(json.status == "OK"){
        success(lat, lng, json.results[0].formatted_address);
      } else {
        // 取得に失敗した場合 (QUERY_LIMITなど)
        failure(lat, lng, json.status + ": " + json.error_message);
      }
    });
  }

  var centerAddress = {
    set: function(text, title, lat, lng){
      $("#center_address").empty().append($("<a/>")
        .attr("href", "https://" + ruli.remoteHost + "/intel?ll=" + lat + "," + lng + "&z=17")
        .attr("title", title)
        .attr("target", "intel")
        .text(text)
      );
    },
    describe: function(){
      var location = gmap.center();
      getAddress(location[0], location[1], function(lat,lng,addr){
        centerAddress.set(addr, location[0] + "/" + location[1], location[0], location[1]);
      }, function(lat,lng,msg){
        centerAddress.set(location[0] + "/" + location[1], "", location[0], location[1]);
        $("#center_address").append($("<span/>")
          .attr("class", "glyphicon glyphicon-exclamation-sign").attr("aria-hidden", "true")
          .attr("title", msg)
        );
      });
    }
  };

  /** 地図の中心位置住所で表示 */
  $("#center_address_search").click(centerAddress.describe);

  /** geolocation を使用した位置情報の取得 */
  var latitude = NaN;
  var longitude = NaN;
  if(navigator.geolocation){
    // 非同期で現在位置を取得
    navigator.geolocation.getCurrentPosition(function(position){
      latitude = position.coords.latitude;
      longitude = position.coords.longitude;
      gmap.initialize("#map", latitude, longitude);
      var location = $("#current_location");
      var mapurl = "https://" + ruli.remoteHost + "/intel?pll=" + latitude + "," + longitude + "&z=17";
      location.empty().append($("<a/>")
       .attr("href", mapurl)
       .attr("target", "intel")
       .append(latitude + "/" + longitude)
      );
      // 現在位置の行政区を取得 (google.maps.Geocoderは失敗時の処理ができない)
      getAddress(latitude, longitude, function(lat,lng,address){
        location.empty().append($("<a/>")
          .attr("href", mapurl)
          .attr("target", "intel")
          .attr("title", lat + "/" + lng)
          .append(address)
        );
      }, function(lat,lng,msg){
        location.append($("<span/>")
          .attr("class", "glyphicon glyphicon-exclamation-sign").attr("aria-hidden", "true")
          .attr("title", msg)
        );
      });
    });
  }

});
