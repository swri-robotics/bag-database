Ext.define('BagDatabase.views.MapWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.mapWindow',
    width: 300,
    height: 300,
    map: null,
    vectorLayers: [],
    constrainHeader: true,
    listeners: {
        afterrender: function(win) {
            var layers = [];
            if (useMapQuest) {
                layers.push(new ol.layer.Tile({
                    source: new ol.source.MapQuest({layer: 'sat'})
                }))
            }
            if (useBing && bingKey && bingKey != '') {
                layers.push(new ol.layer.Tile({
                    source: new ol.source.BingMaps({
                        crossOrigin: 'anonymous',
                        key: bingKey,
                        imagerySet: 'Aerial',
                        maxZoom: 19
                    })
                }))
            }
            win.map = new ol.Map({
                target: win.getId() + '-innerCt', layers: layers,
                view: new ol.View({
                    center: ol.proj.fromLonLat([-98.621488, 29.448972]),
                    zoom: 4
                })
            });
            win.map.updateSize();
        },
        resize: function(win) {
            if (win.map) {
                win.map.updateSize();
            }
        },
        move: function(win) {
            if (win.map) {
                win.map.updateSize();
            }
        }
    },
    initComponent: function() {
        this.callParent(arguments);
    },
    createCircleFeature: function(point, name, color) {
        var feature = new ol.Feature({
            geometry: new ol.geom.Point(point),
            name: name
        });
        feature.setStyle(new ol.style.Style({
            image: new ol.style.Circle({
                fill: new ol.style.Fill({
                    color: color
                }),
                stroke: new ol.style.Stroke({
                    color: 'rgb:(0,0,0)'
                }),
                radius: 4
            })
        }));
        return feature;
    },
    addRoute: function(points) {
        if (points.length == 0) {
            return;
        }
        var tmpPoints = [];
        points.forEach(function(point) {
            tmpPoints.push(ol.proj.fromLonLat(point));
        });
        var routeFeature = new ol.Feature({
            geometry: new ol.geom.LineString(tmpPoints),
            name: 'Bag Route'
        });
        routeFeature.setStyle(new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: 'rgb(255,0,0)',
                width: 2
            })
        }));
        var startFeature = this.createCircleFeature(
            tmpPoints[0], 'Start Point', 'rgb(0,255,0)');
        var endFeature = this.createCircleFeature(
            tmpPoints[tmpPoints.length-1], 'End Point', 'rgb(255,0,0)');
        var source = new ol.source.Vector({
            features: [routeFeature, startFeature, endFeature]
        });
        var layer = new ol.layer.Vector({
            source: source
        });
        this.map.addLayer(layer);
        this.map.getView().fit(source.getExtent(),
            this.map.getSize(),
            { minResolution: 0.29858214173896974 });
    }
});