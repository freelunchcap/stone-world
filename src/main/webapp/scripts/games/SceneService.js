app.service('SceneService', function($q, ResourcesService) {

  var active = null;
  var scenes = {};

  return {
    prepareScene: function(id) {
      if(scenes[id] == null) {
        ResourcesService.scene(id).then(function(scene) {
          scenes[id] = scene;
        });
      }
      active = id;
    },

    move: function() {

    },

    getScene: function() {
      return scenes[active];
    }
  }
});