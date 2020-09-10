// *****************************************************************************
//
// Copyright (c) 2020, Southwest Research Institute® (SwRI®)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Southwest Research Institute® (SwRI®) nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL Southwest Research Institute® BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
//
// *****************************************************************************

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
            if (useTileMap) {
                layers.push(new ol.layer.Tile({
                    source: new ol.source.XYZ({
                        url: tileMapUrl,
                        tileSize: [tileWidthPx, tileHeightPx]
                    })
                }));
            }
            if (useBing && bingKey && bingKey != '') {
                layers.push(new ol.layer.Tile({
                    source: new ol.source.BingMaps({
                        crossOrigin: 'anonymous',
                        key: bingKey,
                        imagerySet: 'Aerial',
                        maxZoom: 19
                    })
                }));
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
        var tmpPoints, routeFeature, startFeature, endFeature, source, layer;
        tmpPoints = [];
        points.forEach(function(point) {
            tmpPoints.push(ol.proj.fromLonLat(point));
        });
        routeFeature = new ol.Feature({
            geometry: new ol.geom.LineString(tmpPoints),
            name: 'Bag Route'
        });
        routeFeature.setStyle(new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: 'rgb(255,0,0)',
                width: 2
            })
        }));
        startFeature = this.createCircleFeature(
            tmpPoints[0], 'Start Point', 'rgb(0,255,0)');
        endFeature = this.createCircleFeature(
            tmpPoints[tmpPoints.length-1], 'End Point', 'rgb(255,0,0)');
        source = new ol.source.Vector({
            features: [routeFeature, startFeature, endFeature]
        });
        layer = new ol.layer.Vector({
            source: source
        });
        this.map.addLayer(layer);
        this.map.getView().fit(source.getExtent(),
            this.map.getSize(),
            { minResolution: 0.29858214173896974 });
    }
});