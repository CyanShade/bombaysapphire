$(function(){

	// #####################################
	// パネルの切り替え

	(function(){
		var panels = [ "portal", "new_portal" ];
		$.each(panels, function(i, value){
			$("#menu_" + value).click(function(){
				$.each(panels, function(j, value2){ $("#" + value2).hide(); })
				$("#" + value).show();
			});
		});
	})()

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
   * ポータルのプロット。
  */
	function draw(data){
		var area = $("#plot_area");
		var w = area.width();
		var h = area.height();
		var g = document.getElementById("plot_area").getContext('2d')
		g.clearRect(0, 0, w, h);
		if(data.length == 0){ return; }
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
				.append($("<td>").attr("style","text-align:right;").text(this["id"]))
				.append($("<td>")
					.append($("<img/>").attr("src", this["image"]).attr("style","height:64px;float:right;"))
					.append(" ")
					.append($("<h4/>").append(this["title"]))
					.append($("<p/>")
						.append($("<a/>")
							.attr("href", "https://" + RemoteHost + "/intel?ll=" + this["latlng"][0] + "," + this["latlng"][1] + "&z=17")
							.attr("target", "intel")
							.text(this["latlng"][0] + "/" + this["latlng"][1])
						)
						.append(this["country"] + this["state"] + this["city"])
					)
				)
			)
		});
	}

	/**
	 * 検索ボタンが押された時のアクション。
	*/
	$("#search").click(function(){
		var query = $("#search_form").serialize();
		$.getJSON("/portal/locations?" + query, function(json){
			list(json);
			var latlng = [ ];
			$.each(json, function(){
				latlng.push(this["latlng"]);
			});
			draw(latlng);
		});
	});

})
