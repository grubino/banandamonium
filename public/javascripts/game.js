function init(spriteSheetImages, $) {

    function get_polygon_scale_factor(limitingDimension, j) {
        return limitingDimension / 2 - ((j+1) * limitingDimension / 14);
    }

    function getPolygonVertex(radius, index) {
        return {
            x: origin.x + radius * Math.cos(index*2*Math.PI / game_settings.playerCount - Math.PI/2),
            y: origin.y + radius * Math.sin(index*2*Math.PI / game_settings.playerCount - Math.PI/2)
        }
    }

    var board = new Board(
        "newBoard",
        game_settings.playerCount,
        game_settings.monkeyCount,
        game_settings.diceCount,
        game_settings.maxStack);

    var monkeyAnimations = [
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40]
    ];

    origin = {
        x: game_settings.width / 2,
        y: game_settings.height / 2
    };

    var canvas = document.createElement('canvas');
    canvas.id = "game-canvas";
    canvas.width = game_settings.width;
    canvas.height = game_settings.height;
    document.body.appendChild(canvas);

    var stage = new createjs.Stage("game-canvas");

    var spriteSheetData = new Array(game_settings.playerCount);
    for(var i = 0; i < game_settings.playerCount; i++) {

        spriteSheetData[i] = {
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
                walkAway: {frames: [3, 4, 5], speed: 0.3}
            }
        };

    }

    var BoardView = function(b, s, st) {
        this.board = b;
        this.spriteSheetData = s;
        this.stage = st;
        this.starts = [];
        this.paths = [];
        this.layers = [];
        this.spriteSize = {
            width: spriteSheetData[0].frames.width*game_settings.spriteScale,
            height: spriteSheetData[0].frames.height*game_settings.spriteScale
        };

        for(var i = 0; i < game_settings.playerCount; i++) {
            var limitingDimension = Math.min(game_settings.width, game_settings.height);
            var coord = getPolygonVertex((limitingDimension / 2 - limitingDimension / 18), i);
            coord.x -= this.spriteSize.width/1.5;
            coord.y -= this.spriteSize.height;
            this.starts[i] = coord;
        }

        var limitingDimension = Math.min(game_settings.width, game_settings.height);
        for (var j = 0; j < 6; j++) {

            var factor = get_polygon_scale_factor(limitingDimension, j);
            var path_factor = factor - (limitingDimension / 14) / 2;
            var color = game_settings.layerColors[j];

            function* generatePath(layerIndex, spriteSize) {

                var sideLength = Math.floor(board.layers[layerIndex].length / game_settings.playerCount);
                for(var i = 0; game_settings.playerCount > i; i++) {
                    var coord = getPolygonVertex(path_factor, i);
                    for(var k = 0; sideLength > k; k++) {
                        var nextCoord = getPolygonVertex(path_factor, i+1);
                        yield {
                            x: coord.x + k * (nextCoord.x - coord.x) / sideLength - spriteSize.width/2,
                            y: coord.y + k * (nextCoord.y - coord.y) / sideLength - spriteSize.height
                        };
                    }
                }
            }
            var path = [];
            var pointGen = generatePath(j, this.spriteSize);
            for(var next = pointGen.next(); next.done === false; next = pointGen.next()) {
                path.push(next.value);
            }
            var polygon = new createjs.Shape();

            polygon.graphics.beginFill(color).drawPolyStar(origin.x, origin.y, factor, game_settings.playerCount, 0, -90).endFill();
            this.layers.push(stage.addChild(polygon));

            path.forEach(function(point) {
                var pathPoint = new createjs.Shape();
                pathPoint.graphics.beginFill("black").drawCircle(point.x+this.spriteSize.width/2, point.y+this.spriteSize.height, this.spriteSize.width/10).endFill();
                this.stage.addChild(pathPoint);
            }, this);
            this.paths.push(path);

        }
        this.spriteSheets = spriteSheetData.map(function(data) {
            return new createjs.SpriteSheet(data);
        });
        this.spriteContainer = new createjs.Container();
        this.sprites = [];
        for(var i = 0; i < game_settings.monkeyCount; i++) {
            this.sprites = this.sprites.concat(
                this.spriteSheets.filter(
                function (spriteSheet, i) {
                    return i < game_settings.playerCount;
                }, this).
                map(function (spriteSheet, i) {
                    var sprite = new createjs.Sprite(spriteSheet, "walkToward");
                    sprite.x = this.starts[i].x;
                    sprite.y = this.starts[i].y;
                    sprite.scaleX = sprite.scaleY = game_settings.spriteScale;
                    sprite.bananaData = {
                        color: i, layer: -1, index: -1
                    };
                    sprite.play();
                    this.spriteContainer.addChild(sprite);
                    return sprite;
                }, this));
        }
        this.stage.addChild(this.spriteContainer);
    }
    BoardView.prototype = {

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

        bringOnBoard: function(sprite, checkBumps) {
            checkBumps = checkBumps === undefined ? false : checkBumps;
            if(sprite.bananaData.tweenedSprite === undefined) {
                sprite.bananaData.tweenedSprite = createjs.Tween.get(sprite);
            }
            var tweenedSprite = sprite.bananaData.tweenedSprite;
            var color = sprite.bananaData.color;
            if(this.board.monkeyStarts[color] === 0) {
                return false;
            }
            var nextMove = this.board.startMove(color);
            var deltaX = boardView.paths[nextMove.layer][nextMove.index].x - this.starts[color].x;
            var deltaY = boardView.paths[nextMove.layer][nextMove.index].y - this.starts[color].y;
            tweenedSprite = tweenedSprite.call(function (deltaX, deltaY) {
                sprite.gotoAndPlay(boardView.determineDirection(deltaX, deltaY));
            }.bind(this, deltaX, deltaY)).to({
                x: boardView.paths[nextMove.layer][nextMove.index].x,
                y: boardView.paths[nextMove.layer][nextMove.index].y
            }, 200).call(function () {
                this.spriteContainer.sortChildren(this.sortSprites);
            }.bind(this));

            if(checkBumps) {
                tweenedSprite.call(function() {
                    sprite.gotoAndStop("faceForward");
                    this.applyBumps(this.board.cascadeBumps());
                }.bind(this));
            }

            sprite.bananaData.index = nextMove.index;
            sprite.bananaData.layer = nextMove.layer;
            this.board.monkeyStarts[color]--;
            return true;
        },

        advance: function(sprite, checkBumps) {
            checkBumps = checkBumps === undefined ? false : checkBumps;
            if(sprite.bananaData.tweenedSprite === undefined) {
                sprite.bananaData.tweenedSprite = createjs.Tween.get(sprite);
            }
            if(sprite.bananaData.index === -1) {
                return this.bringOnBoard(sprite, checkBumps);
            }
            var index = sprite.bananaData.index, layer = sprite.bananaData.layer, color = sprite.bananaData.color;
            var tweenedSprite = sprite.bananaData.tweenedSprite;
            var nextMove = this.board.nextMove(color, layer, index);

            if(!nextMove) {
                return false;
            }
            return this.moveMonkeyTo(sprite, nextMove.layer, nextMove.index, nextMove.subindex, checkBumps);

        },

        findSprite: function(color, layer, index, sprite) {
            return sprite.bananaData.color === color &&
                sprite.bananaData.layer === layer &&
                sprite.bananaData.index === index;
        },

        moveMonkey: function(color, layer, index, distance) {
            var sprite = this.sprites.find(this.findSprite.bind(this, color, layer, index));
            if(sprite === undefined) {
                return false;
            }
            if(this.board.currentPlayer !== color) {
                return false;
            }
            var targetPosition = this.board.moveTarget(color, layer, index, distance);
            if(!this.board.isLegal(targetPosition.layer, targetPosition.index, color)) {
                return false;
            }
            this.board.layers[targetPosition.layer][targetPosition.index].monkeys.push(color);
            for(var i = 0 ; i < distance ; i++) {
                if(!this.advance(sprite, i === distance-1)) {
                    return false;
                }
            }
            this.applyBumps(this.board.cascadeBumps());
            this.board.nextTurn();
            return true;
        },

        moveMonkeyTo: function(sprite, layer, index, subindex, checkBumps) {
            checkBumps = checkBumps === undefined ? false : checkBumps;
            if(sprite.bananaData.tweenedSprite === undefined) {
                sprite.bananaData.tweenedSprite = createjs.Tween.get(sprite);
            }
            var tweenedSprite = sprite.bananaData.tweenedSprite;
            if(layer === -1 && index === -1) {
                var color = sprite.bananaData.color;
                tweenedSprite = tweenedSprite.to({
                    x: this.starts[color].x,
                    y: this.starts[color].y
                }, 500);
                return tweenedSprite;
            }
            var currentIndex = sprite.bananaData.index, currentLayer = sprite.bananaData.layer, color = sprite.bananaData.color;
            var startMove = { index: currentIndex, layer: currentLayer };
            var nextMove = { layer: layer, index: index };
            var subIndexOffsetX = this.spriteSize.width/3*Math.cos(subindex*2*Math.PI / this.board.maxStack - Math.PI/4);
            var subIndexOffsetY = this.spriteSize.height/3*Math.sin(subindex*2*Math.PI / this.board.maxStack - Math.PI/4);
            var subIndexX = this.paths[nextMove.layer][nextMove.index].x + subIndexOffsetX;
            var subIndexY = this.paths[nextMove.layer][nextMove.index].y + subIndexOffsetY;

            var deltaX = subIndexX - this.paths[startMove.layer][startMove.index].x;
            var deltaY = subIndexY - this.paths[startMove.layer][startMove.index].y;

            tweenedSprite = tweenedSprite.call(function (deltaX, deltaY) {
                sprite.gotoAndPlay(boardView.determineDirection(deltaX, deltaY));
            }.bind(this, deltaX, deltaY)).to({
                x: this.paths[nextMove.layer][nextMove.index].x + subIndexOffsetX,
                y: this.paths[nextMove.layer][nextMove.index].y + subIndexOffsetY
            }, 500).call(function () {
                this.spriteContainer.sortChildren(this.sortSprites);
            }.bind(this));

            if(checkBumps) {
                tweenedSprite.call(function() {
                    sprite.gotoAndStop("faceForward");
                    this.applyBumps(this.board.cascadeBumps());
                }.bind(this));
            }
            sprite.bananaData.index = nextMove.index;
            sprite.bananaData.layer = nextMove.layer;

            return tweenedSprite;
        },

        applyBumps: function(bumps) {
            bumps.forEach(function(bump, i, ary) {
                var bumpSprite = this.sprites.find(this.findSprite.bind(this, bump.color, bump.layer, bump.index));
                if(bumpSprite === undefined) {
                    throw "could not find sprite";
                }
                var tweenedBumpSprite = this.moveMonkeyTo(bumpSprite, bump.after_bump.layer, bump.after_bump.index, bump.after_bump.subindex);
                if(!tweenedBumpSprite) {
                    throw "could not move sprite";
                }
                tweenedBumpSprite.call(function() {
                    bumpSprite.gotoAndPlay("walkToward");
                });
            }, this);
            return true;
        }
    }

    var boardView = new BoardView(board, spriteSheetData, stage);

    for(var i = 0; i < boardView.board.playerCount*game_settings.monkeyCount; i++) {
        if (!boardView.moveMonkey(i%boardView.board.playerCount, -1, -1, Math.round(30*Math.random()+1))) {
            console.log("couldn't move monkey!");
        }
    }

    createjs.Ticker.useRAF = true;
    createjs.Ticker.setFPS(20);
    createjs.Ticker.addEventListener("tick", function(event) {
        boardView.stage.update(event);
    });

}
