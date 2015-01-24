$(function(){

  function update_events(page, items){
    var query = "p=" + page + "&i=" + items;
    $.getJSON("/portal/events?" + query, function(json){

      // ページネーション表示
      function f(i){ return function(){ update_events(i, items); return false; }; }
      var pagenation = $("#pagenation").empty();
      var i;
      for(i=0; i<=json.max_page; i++){
        pagenation.append($("<li/>")
          .attr("class", i==page? "active": "")
          .append($("<a/>")
            .attr("href", "?p=" + i + "&i=" + items)
            .click(f(i))
            .text(i + 1)));
      }

      // 各イベントを表示
      $("#events").empty();
      $.each(json.items, function(i, item){
        var message = $("<div/>");
        switch(item.action){
        case "create":
          message.attr("class", "text-info")
            .append("新規ポータル \"").append($("<b/>").text(item.title)).append("\" が追加されました。");
          break;
        case "remove":
          message.attr("class", "text-danger")
            .append("ポータル \"").append($("<b/>").text(item.title)).append("\" が削除されました。");
          break;
        case "change_title":
          message.attr("class", "text-mute")
            .append("ポータル \"" + item.old_value + "\" の名前が \"")
            .append($("<b/>").text(item.new_value)).append("\" に変更されました。");
          break;
        case "change_image":
          message.attr("class", "text-mute")
            .append("ポータル \"" + item.title + "\" の画像が ")
            .append(ruli.createImage(item.title, item.old_value, "3ex"))
            .append(" から ")
            .append(ruli.createImage(item.title, item.new_value, "3ex"))
            .append(" に変更されました。");
          break;
        case "change_location":
          var o = item.old_value.split(",", 2);
          var n = item.new_value.split(",", 2);
          message.attr("class", "text-mute")
            .append("ポータル \"" + item.title + "\" の位置が ")
            .append(ruli.createPinnedLink(item.old_value, item.title, o[0], o[1]))
            .append(" から ")
            .append(ruli.createPortalLink(item.new_value, n[0], n[1]))
            .append(" に変更されました。");
          break;
        default:
          message.text(item.action);
          break;
        }
        var info = $("<div/>")
          .attr("style", "font-size:smaller;")
          .append(ruli.createAddressLink(item.country, item.state, item.city, item.latlng[0], item.latlng[1]))
          .append(" ")
          .append(ruli.createTimestamp(this.created_at));
        if(this.verified_at !== null){
          info.append(" (")
            .append(ruli.createTimestamp(this.verified_at))
            .append(")");
        }
        $("#events")
          .append($("<tr/>")
          .append($("<td/>").append(message).append(info)));
      });
    });
  }

  $(function(){
    update_events(0, 50);
  });
});
