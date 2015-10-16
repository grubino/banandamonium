/**
 * Created by greg.rubino on 10/10/15.
 */
var model = model || {};
var view = view || {};


function createBoardModel(gameId) {
    var Board = Backbone.Model.extend({
        url: '/boards/'+gameId,
        idAttribute: 'turnIndex'
    });
    var Boards = Backbone.Collection.extend({
        url: '/boards/'+gameId,
        model: Board
    });
    model.boards = new Boards;
}

function createBoardView(gameId, playerId) {
    var BoardView = Backbone.View.extend({

        el: "#game-canvas",

        events: {},

        initialize: function() {

            this.origin = {
                x: game_settings.width / 2,
                y: game_settings.height / 2
            };

            this.stage = new createjs.Stage("game-canvas");
            this.spriteSheetData = new Array(this.model.get('playerCount'));
            for(var i = 0; i < this.model.get('playerCount'); i++) {

                this.spriteSheetData[i] = {
                    images: [spriteSheetImages[i%spriteSheetImages.length]],
                    frames: {width: 17, height: 27},
                    animations: {
                        faceForward: 6,
                        faceLeft: 15,
                        faceRight: 0,
                        faceAway: 3,
                        walkLeft: {frames: [14, 15, 16], speed: 0.3},
                        walkRight: {frames: [0, 1, 2], speed: 0.3},
                        walkToward: {frames: [8, 9, 10], speed: 0.3},
                        walkAway: {frames: [3, 4, 5], speed: 0.3},
                        wave: {frames: [29, 30, 31], speed: 0.3}
                    }
                };

            }
            this.starts = [];
            this.paths = [];
            this.layers = [];
            this.spriteSize = {
                width: this.spriteSheetData[0].frames.width*game_settings.spriteScale,
                height: this.spriteSheetData[0].frames.height*game_settings.spriteScale
            };

        },

        _getPolygonVertex: function (radius, index) {
            return {
                x: this.origin.x + radius * Math.cos(index*2*Math.PI / this.model.get('playerCount') - Math.PI/2),
                y: this.origin.y + radius * Math.sin(index*2*Math.PI / this.model.get('playerCount') - Math.PI/2)
            }
        },

        _getPolygonScaleFactor: function (limitingDimension, j) {
            return limitingDimension / 2 - ((j+1) * limitingDimension / 14);
        },

        _generatePath: function* (layerIndex, spriteSize, path_factor) {

            if(layerIndex === 5) {
                yield {
                    x: this.origin.x - spriteSize.width/2,
                    y: this.origin.y - spriteSize.height
                };
            }
            var sideLength = Math.floor(this.model.get('layers')[layerIndex].length / this.model.get('playerCount'));
            for(var i = 0; this.model.get('playerCount') > i; i++) {
                var coord = this._getPolygonVertex(path_factor, i);
                for(var k = 0; sideLength > k; k++) {
                    var nextCoord = this._getPolygonVertex(path_factor, i+1);
                    yield {
                        x: coord.x + k * (nextCoord.x - coord.x) / sideLength - spriteSize.width/2,
                        y: coord.y + k * (nextCoord.y - coord.y) / sideLength - spriteSize.height
                    };
                }
            }
        },

        startAssignDie: function(event) {
            event.currentTarget.x = event.stageX;
            event.currentTarget.y = event.stageY;
            this.stage.update();
        },

        finishAssignDie: function(event) {
            var sprite = this.spriteContainer.getObjectUnderPoint(event.currentTarget.x, event.currentTarget.y, 0);
            if(sprite && sprite.bananaData.color === playerId) {
                var move = {
                    playerId: playerId,
                    layerIndex: sprite.bananaData.layer,
                    placeIndex: sprite.bananaData.index,
                    monkeyCount: sprite.bananaData.stackSize,
                    distance: event.currentTarget.bananaData.dieValue
                };
                sprite.bananaData.stackSize = 1;
                this.turn.moves.push(move);
                this.dice.removeChild(event.currentTarget);
                if(this.dice.numChildren === 0) {
                    this.executeMove();
                }
            }
            var die = this.dice.getObjectUnderPoint(event.currentTarget.x, event.currentTarget.y, 0);
            if(die) {
                this.createDie(
                    die.parent.bananaData.dieValue.concat(event.currentTarget.bananaData.dieValue),
                    die.parent.bananaData.dieIndex);
                this.dice.removeChild(die.parent, event.currentTarget);
            }
        },

        createDie: function(dieVal, i) {
            var bg = new createjs.Shape();
            bg.graphics.beginFill("red").drawRoundRect(
                0, 0, game_settings.width/20, game_settings.height/20, 5);
            var label = new createjs.Text(dieVal, "bold "+game_settings.height/20+"px Fixed", "#ffffff");
            label.textAlign = "center";
            label.textBaseline = "center";
            label.x = game_settings.width/40;
            label.y = game_settings.height/40+10;
            var button = new createjs.Container();
            button.x = 20 + game_settings.width/10 + i*game_settings.width/20;
            button.y = 20;
            button.addChild(bg, label);
            button.bananaData = {dieValue: dieVal, dieIndex: i};
            button.on("pressmove", this.startAssignDie.bind(this));
            button.on("pressup", this.finishAssignDie.bind(this));
            this.dice.addChild(button);
        },

        unhighlighSprites: function() {
            this.sprites.forEach(function(sprite) {
                sprite.gotoAndStop("faceForward");
            });
        },

        highlightSprites: function() {
            this.sprites.forEach(function(sprite) {
                if(sprite.bananaData.color === this.model.get('currentPlayer')) {
                    sprite.gotoAndPlay("wave");
                }
            }, this);
        },

        postRoll: function(roll) {
            if(this.dice) {
                this.dice.removeAllChildren();
            }
            this.dice = new createjs.Container();
            this.turn = {playerIndex: playerId, gameId: gameId, moves: [], bananaCards: []};
            roll.diceRolls.map(function(roll) { return [roll]; }).forEach(this.createDie, this);
            this.highlightSprites();
            this.stage.addChild(this.dice);
        },

        executeMove: function() {
            $.ajax({
                cache: false,
                url: '/move/'+gameId+'/'+playerId,
                dataType: 'json',
                contentType: 'application/json',
                data: JSON.stringify(this.turn),
                type: "POST",
                error: function(response, status, error) { alert("could not move: "+JSON.stringify(response)); },
            })
        },

        rollDice: function(event) {
            event.preventDefault();
            this.unpairSprites();
            if(this.model.get('currentPlayer') === playerId) {

                $.ajax({
                    cache: false,
                    url: '/roll/'+gameId+'/'+playerId,
                    dataType: 'json',
                    error: function(response, status, error) { alert("could not roll: "+JSON.stringify(response)); },
                    success: this.postRoll.bind(this)
                });

            }
        },

        _initialRender: function() {

            var limitingDimension = Math.min(game_settings.width, game_settings.height);

            var rollButton = new createjs.Shape();
            rollButton.graphics.beginFill("red").drawRoundRect(0, 0, game_settings.width/8, game_settings.height/18, 10);
            var label = new createjs.Text("Roll Dice", "bold "+game_settings.height/50+"px Fixed", "#ffffff");
            label.textAlign = "center";
            label.textBaseline = "center";
            label.x = game_settings.width/20;
            label.y = game_settings.height/40 + 5;

            this.rollButton = new createjs.Container();
            this.rollButton.x = 20;
            this.rollButton.y = 20;
            this.rollButton.addChild(rollButton, label);
            this.rollButton.on("click", this.rollDice.bind(this));
            this.stage.addChild(this.rollButton);

            for(var i = 0; i < this.model.get('playerCount'); i++) {
                var coord = this._getPolygonVertex((limitingDimension / 2) - this.spriteSize.height, i);
                coord.x -= this.spriteSize.width/2;
                coord.y -= this.spriteSize.height/2;
                this.starts[i] = coord;
            }

            for (var j = 0; j < this.model.get('layers').length; j++) {

                var factor = this._getPolygonScaleFactor(limitingDimension, j);
                var path_factor = factor - (limitingDimension / 12) / 2;
                var color = game_settings.layerColors[j];

                var path = [];
                var pointGen = this._generatePath(j, this.spriteSize, path_factor);
                for(var next = pointGen.next(); next.done === false; next = pointGen.next()) {
                    path.push(next.value);
                }
                var polygon = new createjs.Shape();

                polygon.graphics.beginFill(color).drawPolyStar(this.origin.x, this.origin.y, factor, this.model.get('playerCount'), 0, -90).endFill();
                this.layers.push(this.stage.addChild(polygon));

                path.forEach(function(point, k) {
                    var pathPoint = new createjs.Shape();
                    if(j === 1 && k % 4 === 0) {
                        pathPoint.graphics.beginFill("yellow").drawCircle(point.x+this.spriteSize.width/2, point.y+this.spriteSize.height, this.spriteSize.width/5).endFill();
                    } else if(j === 2 && (k+1) % 3 === 0) {
                        pathPoint.graphics.beginFill("yellow").drawCircle(point.x+this.spriteSize.width/2, point.y+this.spriteSize.height, this.spriteSize.width/5).endFill();
                    } else {
                        pathPoint.graphics.beginFill("black").drawCircle(point.x + this.spriteSize.width / 2, point.y + this.spriteSize.height, this.spriteSize.width / 10).endFill();
                    }
                    this.stage.addChild(pathPoint);
                }, this);
                this.paths.push(path);

            }
            this.spriteSheets = this.spriteSheetData.map(function(data) {
                return new createjs.SpriteSheet(data);
            });
            this.spriteContainer = new createjs.Container();
            this.sprites = [];
            for(var i = 0; i < this.model.get('monkeyStarts').length; i++) {
                for (var j = 0; j < this.model.get('monkeyStarts')[i].length; j++) {
                    var spriteSheet = this.spriteSheets[i % this.spriteSheets.length];
                    var sprite = new createjs.Sprite(spriteSheet, "walkToward");
                    sprite.x = this.starts[i].x + j*Math.round(20*(Math.cos(i * 2 * Math.PI / this.model.get('playerCount'))));
                    sprite.y = this.starts[i].y + j*Math.round(20*(Math.sin(i * 2 * Math.PI / this.model.get('playerCount'))));
                    sprite.scaleX = sprite.scaleY = game_settings.spriteScale;
                    sprite.bananaData = {
                        color: i, layer: -1, index: -1, stackSize: 1,
                        monkeyId: this.model.get('monkeyStarts')[i][j].monkeyId
                    };
                    sprite.addEventListener("click", this.combineSprites.bind(this));
                    sprite.play();
                    this.spriteContainer.addChild(sprite);
                    this.sprites = this.sprites.concat(sprite);
                }
            }
            for(var i = 0; i < this.model.get('layers').length; i++) {
                for(var j = 0; j < this.model.get('layers')[i].length; j++) {
                    var monkeys = this.model.get('layers')[i][j].monkeys;
                    if(monkeys.length > 0) {
                        monkeys.forEach(function(monkey, k) {
                            var spriteSheet = this.spriteSheets[monkey.playerId % this.spriteSheets.length]
                            var sprite = new createjs.Sprite(spriteSheet, "faceForward");
                            var subindexOffsets = this.getSubindexOffsets(k);
                            sprite.x = this.paths[i][j].x + subindexOffsets[0];
                            sprite.y = this.paths[i][j].y + subindexOffsets[1];
                            sprite.scaleX = sprite.scaleY = game_settings.spriteScale;
                            sprite.bananaData = {
                                color: monkey.playerId, layer: i, index: j, stackSize: 1,
                                monkeyId: monkey.monkeyId
                            };
                            sprite.addEventListener("click", this.combineSprites.bind(this));
                            sprite.play();
                            this.spriteContainer.addChild(sprite);
                            this.sprites = this.sprites.concat(sprite);
                        }, this);
                    }
                }
            }
            this.spriteContainer.sortChildren(this.sortSprites);
            this.stage.addChild(this.spriteContainer);

            createjs.Ticker.useRAF = true;
            createjs.Ticker.setFPS(20);
            var context = this;
            createjs.Ticker.addEventListener("tick", function(event) {
                context.stage.update(event);
            });

        },

        scaleSprite: function(sprite) {
            sprite.scaleX = game_settings.spriteScale + sprite.bananaData.stackSize - 1;
            sprite.scaleY = game_settings.spriteScale + sprite.bananaData.stackSize - 1;
        },

        combineSprites: function(event) {
            var sprite = event.currentTarget;
            var spriteLayer = sprite.bananaData.layer;
            var spriteIndex = sprite.bananaData.index;
            var spriteColor = sprite.bananaData.color;
            var maxStack = this.model.get('maxStack');

            sprite.bananaData.stackSize = sprite.bananaData.stackSize % maxStack + 1;
            this.scaleSprite(sprite);
            var pairable = this.sprites.filter(function (s) {
                return s.bananaData.monkeyId !== sprite.bananaData.monkeyId &&
                    s.bananaData.playerId === sprite.bananaData.playerId &&
                    s.bananaData.layer === sprite.bananaData.layer &&
                    s.bananaData.index === sprite.bananaData.index;
            });
            if(pairable.length > 0) {
                pairable[0].bananaData.stackSize = pairable[0].bananaData.stackSize % maxStack + 1;
                this.scaleSprite(pairable[0])
            }
        },

        unpairSprites: function() {
            this.sprites.forEach(function(sprite) {
                sprite.bananaData.stackSize = 1;
                this.scaleSprite(sprite);
            }, this);
        },

        render: function() {

            this.el.width = game_settings.width;
            this.el.height = game_settings.height;

            this._initialRender();
            this.collection.on("add", this._onNewBoardState.bind(this));

        },

        getSubindexOffsets: function(subindex) {

            var subIndexOffsetX = this.spriteSize.width/3*Math.cos(subindex*2*Math.PI / this.model.get('maxStack') - Math.PI/4);
            var subIndexOffsetY = this.spriteSize.height/3*Math.sin(subindex*2*Math.PI / this.model.get('maxStack') - Math.PI/4);

            return [subIndexOffsetX, subIndexOffsetY];

        },

        sortSprites: function(a, b) {
            return a.y > b.y ? 1 : a.y === b.y ? 0 : -1;
        },

        determineDirection: function(deltaX, deltaY) {
            if(Math.abs(deltaX) > Math.abs(deltaY)) {
                if(deltaX > 0) {
                    return "walkRight";
                } else {
                    return "walkLeft";
                }
            } else {
                if(deltaY > 0) {
                    return "walkToward";
                } else {
                    return "walkAway";
                }
            }
        },

        advance: function(sprite, toLayer, toIndex) {
            var nextMove = {};
            var layers = this.model.get('layers');
            var index = sprite.bananaData.index, layer = sprite.bananaData.layer, color = sprite.bananaData.color;
            if(sprite.bananaData.tweenedSprite === undefined) {
                sprite.bananaData.tweenedSprite = createjs.Tween.get(sprite);
            }
            if(sprite.bananaData.index === -1 && sprite.bananaData.layer === -1) {
                nextMove.layer = 1;
                nextMove.index = layers[1].findIndex(
                    function(place) { return place.slideDown === color; });
                nextMove.subindex = layers[nextMove.layer][nextMove.index].monkeys.length;
                return this.moveMonkeyTo(sprite, nextMove.layer, nextMove.index, nextMove.subindex);
            }

            if(layer === toLayer && index === toIndex) {
                return false;
            }

            if(layers[layer][index].slideUp === color) {
                nextMove.layer = layer+1;
                if(nextMove.layer < 5) {
                    nextMove.index = layers[nextMove.layer].findIndex(
                        function (place) {
                            return place.slideDown === color;
                        });
                } else {
                    nextMove.index = 0;
                }
            } else {
                nextMove.layer = layer;
                nextMove.index = (index+1)%layers[layer].length;
            }
            nextMove.subindex = layers[nextMove.layer][nextMove.index].monkeys.length;
            return this.moveMonkeyTo(sprite, nextMove.layer, nextMove.index, nextMove.subindex);
        },

        moveMonkey: function(sprite, layer, index) {
            var tweenedSprite;
            for(var result = true; result; result = this.advance(sprite, layer, index)) {
                tweenedSprite = result;
            }
            return tweenedSprite;
        },

        prepareSpriteToMove: function(sprite) {
            if(sprite.bananaData.tweenedSprite === undefined) {
                sprite.bananaData.tweenedSprite = createjs.Tween.get(sprite);
            }
            return sprite.bananaData.tweenedSprite;
        },

        moveMonkeyToPosition: function(sprite, x, y) {
            var tweenedSprite = this.prepareSpriteToMove(sprite);
            return tweenedSprite.to({x: x, y: y});
        },

        moveMonkeyTo: function(sprite, layer, index, subindex) {
            if(sprite.bananaData.tweenedSprite === undefined) {
                sprite.bananaData.tweenedSprite = createjs.Tween.get(sprite);
            }
            var tweenedSprite = this.prepareSpriteToMove(sprite);
            var color = sprite.bananaData.color;
            if(layer === -1 && index === -1) {
                tweenedSprite = tweenedSprite.to({
                    x: this.starts[color].x,
                    y: this.starts[color].y
                }, 500);
                return tweenedSprite;
            }
            var currentIndex = sprite.bananaData.index, currentLayer = sprite.bananaData.layer, color = sprite.bananaData.color;
            var startMove = { index: currentIndex, layer: currentLayer };
            var nextMove = { layer: layer, index: index };
            var subIndexOffsets = this.getSubindexOffsets(subindex);
            var subIndexOffsetX = subIndexOffsets[0];
            var subIndexOffsetY = subIndexOffsets[1];
            var subIndexX = this.paths[nextMove.layer][nextMove.index].x + subIndexOffsetX;
            var subIndexY = this.paths[nextMove.layer][nextMove.index].y + subIndexOffsetY;

            var deltaX;
            var deltaY;

            if(startMove.layer === -1 && startMove.index === -1) {
                deltaX = subIndexX - this.starts[color].x;
                deltaY = subIndexY - this.starts[color].y;
            } else {
                deltaX = subIndexX - this.paths[startMove.layer][startMove.index].x;
                deltaY = subIndexY - this.paths[startMove.layer][startMove.index].y;
            }

            tweenedSprite = tweenedSprite.call(function (deltaX, deltaY) {
                sprite.gotoAndPlay(this.determineDirection(deltaX, deltaY));
            }.bind(this, deltaX, deltaY)).to({
                x: this.paths[nextMove.layer][nextMove.index].x + subIndexOffsetX,
                y: this.paths[nextMove.layer][nextMove.index].y + subIndexOffsetY
            }, 500).call(function () {
                this.spriteContainer.sortChildren(this.sortSprites);
            }.bind(this));

            sprite.bananaData.index = nextMove.index;
            sprite.bananaData.layer = nextMove.layer;

            return tweenedSprite;
        },

        _moveMonkeyToProperPlace: function(sprite, board, i, j) {
            this.moveMonkeyTo(sprite, i, j, board.get('layers')[i][j].monkeys.findIndex(
                function(monkey) {
                    return monkey.monkeyId === sprite.bananaData.monkeyId;
                })).call(function() { sprite.gotoAndStop("faceForward"); });
            sprite.scaleX = game_settings.spriteScale;
            sprite.scaleY = game_settings.spriteScale;
            sprite.bananaData.stackSize = 1;
            this.scaleSprite(sprite);
        },

        _onNewBoardState: function(newBoard, boards) {
            this.unhighlighSprites();
            this.sprites.forEach(function(sprite) {
                if(sprite.bananaData.layer === -1 && sprite.bananaData.index === -1) {
                    if(newBoard.get('monkeyStarts')[sprite.bananaData.color].findIndex(
                        function(monkey) {
                            return monkey.monkeyId === sprite.bananaData.monkeyId;
                        }) !== -1) {
                        return;
                    }
                } else if(newBoard.get('layers')[sprite.bananaData.layer][sprite.bananaData.index].monkeys.findIndex(
                    function(monkey) {
                        return monkey.monkeyId === sprite.bananaData.monkeyId;
                    }) !== -1) {
                    return;
                }
                for(var i = 0; i < newBoard.get('layers').length; i++) {
                    for(var j = 0; j < newBoard.get('layers')[i].length; j++) {
                        var searchIndex = newBoard.get('layers')[i][j].monkeys.findIndex(
                            function(monkey) {
                                return monkey.monkeyId == sprite.bananaData.monkeyId;
                            });
                        if(searchIndex !== -1) {
                            if(sprite.bananaData.layer > i) {
                                this._moveMonkeyToProperPlace(sprite, newBoard, i, j);
                            } else {
                                this.moveMonkey(sprite, i, j);
                                this._moveMonkeyToProperPlace(sprite, newBoard, i, j);
                            }
                            this.sprites.filter(
                                function(sprite) {
                                    return sprite.bananaData.layer === i && sprite.bananaData.index === j;
                                }).forEach(
                                function(s) {
                                    this._moveMonkeyToProperPlace(s, newBoard, i, j);
                                }, this);
                            return;
                        }
                    }
                }
                this.moveMonkeyTo(sprite, -1, -1, -1);
            }, this);
            this.model = newBoard;
        }

    });
    model.boards.fetch({
        success: function(boards) {
            view.boardView = new BoardView({el: $('#game-canvas'), collection: boards, model: boards.at(0)});
            view.boardView.render();
        }
    });
    var updateInterval = setInterval(model.boards.fetch.bind(model.boards), 1000);
}