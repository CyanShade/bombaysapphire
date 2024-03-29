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
    if(country === null && state === null && city === null){
      return create_portal_link("住所未解決", lat, lng);
    }
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

  /** 緯度/経度から距離を計算。 */
  function distance(lat0, lng9, lat1, lng1){
    var latM = 40054782/360 * Math.abs(lat1 - lat2);    // 緯度方向の距離
    //var lngM =
  }

  /** 数値をフォーマット */
  function display_number(num){
    return String(Math.floor(num)).replace( /(\d)(?=(\d\d\d)+(?!\d))/g, '$1,');
  }

  /** ページネーションの表示 */
  function paginate(selector, page, pages, min, max, onclick, href){
    var pagination = $(selector).empty();
    function add(i){
      pagination.append($("<li/>")
        .attr("class", i==page? "active": "")
        .append($("<a/>")
          .attr("href", href(i))
          .click(onclick(i))
          .text(i + 1)));
    }
    function addSeparator(){
      pagination.append($("<li/>")
        .attr("class", "disabled")
        .append($("<a/>").attr("href", "#").text("…")));
    }
    var begin = Math.max(min, page - Math.floor(pages / 2));
    var end = Math.min(max, begin + pages);
    if(begin > min) add(min);
    if(begin > min + 1) addSeparator();
    var i;
    for(i=begin; i<=end; i++) add(i);
    if(end < max - 1) addSeparator();
    if(end < max) add(max);
  }

  function millis_to_display_timespan(tm){
    var days = Math.floor(tm / 24 / 60 / 60 / 1000);
    var hours = Math.floor(tm / 60 / 60 / 1000) % 24;
    var minutes = Math.floor(tm / 60 / 1000) % 60;
    var seconds = Math.floor(tm / 1000) % 60;
    if(days > 0){
      return days + "日 " + hours + ":" + ("0"+minutes).substring(0,2);
    } else if(hours > 0 || minutes > 5){
      return hours + ":" + ("0"+minutes).substring(0,2);
    }
    return "今";
  }

  /*
   * 緯度/経度から行政区を取得。
   * @@param latitude 緯度
   * @@param longitude 経度
   * @@param success(address)
   * @@param failure(message)
   */
  function get_address(latitude, longitude, success, failure){
    var goecode = "http://maps.googleapis.com/maps/api/geocode/json?latlng=" + latitude + "," + longitude + "&sensor=false";
    $.getJSON(goecode, function(json){
      if(json.status == "OK"){
        success(json.results[0].formatted_address);
      } else if(failure){
        // 取得に失敗した場合 (QUERY_LIMITなど)
        failure(json.status + ": " + json.error_message);
      }
    });
    return;
  }

  /*
   * geolocation を使用して非同期で緯度/経度の取得。
   * @@param success(latitude, longitude)
   */
  function get_current_location(success){
    if(navigator.geolocation){
      navigator.geolocation.getCurrentPosition(function(position){
        success(position.coords.latitude, position.coords.longitude);
      });
    }
    return;
  }

  /*
   * geolocation を使用して非同期で住所の取得。
   * @@param success(address)
   * @@param failure(latitude, longitude, message);
   */
  function get_current_address(success, failure){
    get_current_location(function(lat, lng){
      get_address(lat, lng, success, function(msg){
        failure(lat, lng, msg);
      });
    });
    return;
  }

  ruli = {
    remoteHost: RemoteHost,
    createAddressLink: create_address_link,
    createPortalLink: create_portal_link,
    createPinnedLink: create_pinned_link,
    createTimestamp: create_timestamp,
    createImage: create_image,
    paginate: paginate,
    displayNumber: display_number,
    millisToDisplayTimeSpan: millis_to_display_timespan,
    gmap: {
      distance: function(from, to){ return google.maps.geometry.spherical.computeDistanceBetween(from, to); },
      heading: function(from, to){ return google.maps.geometry.spherical.computeHeading(from, to); },
      displayHeading: function(h){
        var l = ["北","北北東","北東","東北東","東","東南東","東南","南南東","南","南南西","南西","西南西","西","西北西","北西","北北西"];
        h += 360 / l.length / 2;
        if(h < 0){
          h = 360 + h;
        }
        return l[Math.floor((h / 360 * l.length)) % l.length];
      },
      pinnedGMapUrl: function(title,ll){
        return "http://maps.google.co.jp/maps?q=" + ll.lat() + "," + ll.lng() + "+" + encodeURIComponent(title);
      },
      getAddress: get_address,
      getCurrentLocation: get_current_location,
      getCurrentAddress: get_current_address
    }
  };

});
