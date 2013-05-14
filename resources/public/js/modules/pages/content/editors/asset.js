(function (global) {
  global.caribou = global.caribou || {};
  var editors = global.caribou.editors;
  if (!editors) {
    throw "editors/base.js and editors/fields.js have not been included";
  }

  function AssetFieldEditor( options ) { editors.PartFieldEditor.call( this, options ); }
  $.extend( AssetFieldEditor.prototype, editors.PartFieldEditor.prototype, {
    syncToDOM: function() {
      var asset = this.value.value;
      if ( asset ) {
        $("img#" + this.field.slug).attr({ src: "/" + asset.path });
      }
    },
    syncFromDOM: function() {},
    attach: function() {
      var self = this;
      $("#" + self.model.slug + "-" + self.field.slug).find("a").click( function(e) {
        e.preventDefault();
        console.log(self, "Upload/choose an image");
        return self.uploadOrChoose();
      });
    },
    uploadOrChoose: function() {
      var self = this;

      var editor = new editors.AssetEditor({
        from: self,
        field: self.field,
        model: self.api().model("asset"),
        submit: function( value, next ) {
          self.value.value = value;
          self.value.id = (value? value.id : null);
          self.callbackWithValue("sync", self.value, next);
        }
      });

      editor.load( function( data, error, jqxhr ) {
        editor.template = data.template;
        editor.value = data.state || editor.value;
        self.stack().push( editor );
      });

      return false;
    }
  });

  function AssetEditor( options ) {
    editors.Editor.call( this, options );
    this.field = options.field;
    this._assetsById = {};
  }
  $.extend( AssetEditor.prototype, editors.Editor.prototype, {
    description: function() { return this.field.slug },
    attach: function() {
      var self = this;
      $("#upload-asset").ajaxfileupload({
        action: self.api().routeFor("upload-asset"),
        onComplete: function(response) {
          self.value = response.state;
          $("#current-image").attr("src", "/" + self.value.path);
          self.load(function( data, error, jqxhr ) {
            self.refreshAssets();
          });
        }
      });
      $("#upload-button").click( function(e) {
        e.preventDefault();
        self.upload(e);
      });
      $("#asset-search-button").click( function(e) {
        e.preventDefault();
        self.refreshAssets();
      })
      self.refreshAssets();
    },
    load: function( success ) {
      var self = this;
      var route = self.api().routeFor( "editor-content", {
        id: self.get("id", ""),
        model: "asset",
        template: "_asset.html"
      });
      $.ajax({ url: route, success: success });
    },
    loadAssets: function(assets) {
      var self = this;
      self._assetsById = self._assetsById || {};
      _( assets ).each( function(a) { self._assetsById[a.id] = a } );
    },
    refreshAssets: function(page) {
      var self = this;
      $.ajax({
        url: self.api().routeFor( "editor-content",
          { model: "asset", template: "_existing_assets.html", page: (page || "0"), size: 50 }
        ),
        type: "GET",
        success: function( data, error, jqxhr ) {
          self.loadAssets(data.state);
          $("#assets").html( data.template );
          $("#assets").find("select[name=images]").imagepicker({ show_label: false });
          $("#assets a").click( function(e) {
            e.preventDefault();
            self.refreshAssets($(this).data().page);
          });
        }
      });
    },
    syncFromDOM: function() {
      console.log("AssetEditor syncing from DOM");
      var assetId = $("select[name=images]").val();
      if ( assetId ) {
        this.value = asset = this._assetsById[ assetId ];
      }
      console.log( this.value );
    }
  });

  editors.AssetFieldEditor = AssetFieldEditor;
  editors.AssetEditor = AssetEditor;
})(window);
