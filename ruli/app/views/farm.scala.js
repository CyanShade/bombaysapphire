$(function(){

  function select(id){
    $(".farm").removeClass("active");
    $("#farm_" + id).addClass("active");
    $("#farm_desc").load("/api/1.0/farm/" + id);
  }

  // ファーム一覧を表示
  $("#farm_list").load("@routes.FarmController.farmListAPI", function(){
    // 動的生成されたファームリンクのクリック時に該当ファームの詳細情報を表示するよう設定
    $(".farm").click(function(){
      select($(this).data("farm_id"));
    });
  });

  /* ページロード時に #!<id> のファームをデフォルト表示 */
})