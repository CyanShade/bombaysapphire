$(function(){
  var currentPortals = [];    // 現在表示中のポータル

  /**
   * ポータル情報の一覧表示。
  */
  function list(data){
    $("#portal_count").text(ruli.displayNumber(data.length));
    var table = $("#portals");
    table.empty();
    var center = gmap.center();
    var from = new google.maps.LatLng(center[0], center[1]);
    $.each(data, function(){
      var to = new google.maps.LatLng(this.latlng[0], this.latlng[1]);
      var distance = google.maps.geometry.spherical.computeDistanceBetween(from, to);
      table.append($("<tr/>")
        // .append($("<td>").attr("style","text-align:right;").text(this.id))
        .append($("<td>")
          .append($("<img/>")
            .attr("src", this.image)
            .attr("style","max-width:64px;max-height:64px;float:right;")
            .attr("title", this.title)
            .attr("alt", this.title)
          )
          .append($("<h5/>").append(this.title))
          .append($("<p/>")
            .attr("style", "font-size:smaller;")
            .append(ruli.createAddressLink(this.country, this.state, this.city, this.latlng[0], this.latlng[1]))
            .append($("<span/>")
              .attr("style", "font-size:0px;")  // コピペ時に現れる隠しテキストとして
              .text(" https://" + ruli.RemoteHost + "/intel?pll=" + this.latlng[0] + "," + this.latlng[1] + "&z=17")
            )
            .append(" ")
            .append(ruli.displayNumber(distance))
            .append("m ")
            .append(ruli.createTimestamp(this.created_at))
          )
        )
      );
    });
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
    var portalMarkers = [];
    var centerMarkers = [];
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
      portalMarkers = [];
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
          google.maps.event.addListener(map, "drag", mapMoving);
          google.maps.event.addListener(map, "dragend", mapChanged);
          google.maps.event.addListener(map, "zoom_changed", mapChanged);
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
      /**
       * 表示領域変更によるポータルの更新。
      */
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
          portalMarkers.push(marker);
        });
      },
      /**
       * 地図同期の設定。
      */
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
        if(show){
          $.each(portalMarkers, function(){
            this.setMap(map);
          });
        } else {
          $.each(portalMarkers, function(){
            this.setMap(null);
          });
        }
      },
      /** センターポジションの参照 */
      center: function(){
        var c = map.getCenter();
        return [ c.lat(), c.lng() ];
      }
    };
  })();

  /**
   * geolocation を使用した位置情報の取得。
  */
  var latitude = NaN;
  var longitude = NaN;
  if(navigator.geolocation){
    navigator.geolocation.getCurrentPosition(function(position){
      latitude = position.coords.latitude;
      longitude = position.coords.longitude;
      gmap.initialize("#map", latitude, longitude);
      var location = $("#current_location");
      var goecode = "http://maps.googleapis.com/maps/api/geocode/json?latlng=" + latitude + "," + longitude + "&sensor=false";
      var mapurl = "https://" + ruli.RemoteHost + "/intel?pll=" + latitude + "," + longitude + "&z=17";
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
