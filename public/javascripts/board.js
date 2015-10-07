function generate_moves(dice_count) {
    moves = [];
    for (var i = 0; i < dice_count; i++) {
        moves[i] = Math.floor(Math.random() * 5) + 1;
    }
    return moves;
}

var Board = function (id, playerCount, monkeyCount, diceCount, maxStack) {

    this.gameId = id
    this.layers = [[], [], [], [], [], [], [{monkeys: []}]];
    this.monkeyStarts = [];
    this.maxStack = maxStack;
    this.currentPlayer = 0;
    this.diceCount = diceCount;
    this.turnIndex = 0;
    this.bananaCards = {deck: [], active: [], discard: []};
    this.playerCount = playerCount;

    for (var i = 0; i < playerCount; i++) {

        this.monkeyStarts[i] = monkeyCount;
        this.layers[0] =
            this.layers[0].concat([{monkeys: [], slideDown: i},
                {monkeys: []},
                {monkeys: []},
                {monkeys: [], slideUp: (i + 1) % playerCount}]);
        this.layers[1] =
            this.layers[1].concat([{monkeys: [], slideDown: i},
                {monkeys: []},
                {monkeys: []},
                {monkeys: [], slideUp: (i + 1) % playerCount}]);
        this.layers[2] =
            this.layers[2].concat([{monkeys: [], slideDown: i},
                {monkeys: []},
                {monkeys: [], slideUp: (i + 1) % playerCount}]);
        this.layers[3] =
            this.layers[3].concat([{monkeys: [], slideDown: i},
                {monkeys: []},
                {monkeys: [], slideUp: (i + 1) % playerCount}]);
        this.layers[4] =
            this.layers[4].concat([{monkeys: [], slideDown: i},
                {monkeys: []},
                {monkeys: [], slideUp: (i + 1) % playerCount}]);
        this.layers[5] =
            this.layers[5].concat([{monkeys: [], slideDown: i},
                {monkeys: [], slideUp: (i + 1) % playerCount}]);

    }

}

Board.prototype = {

    hasBananaCard: function(layer, index) {
        if(layer === 1) {
            return this.layers[layer][index].slideDown !== undefined;
        } else if(layer === 2) {
            return this.layers[layer][index].slideUp !== undefined;
        }
        return false;
    },

    nextTurn: function() {
        this.currentPlayer = (this.currentPlayer + 1) % this.playerCount;
    },

    isLegal: function (layer, position, color) {
        if(this.layers[layer][position].monkeys.length === this.maxStack) {
            return this.layers[layer][position].monkeys.filter(function(monkey) {
                    return monkey === color;
                }).length >= Math.floor(this.maxStack/2);
        } else {
            return true;
        }
    },

    canBump: function (layer, position, color) {
        var other_monkeys = this.layers[layer][position].monkeys.filter(function (x) {
            return x != color;
        });
        return other_monkeys.length == 1;
    },

    slideDown: function (color, layer, index) {
        var monkeyIndex = this.layers[layer][index].monkeys.indexOf(color);
        if(monkeyIndex === -1) {
            return false;
        }
        if(layer === 0) {
            var slideMove = {layer: -1, index: -1, subindex: -1};
            this.monkeyStarts[color]++;
        } else if(layer < 5) {
            var layerProgress = index / this.layers[layer].length;
            var downIndex = Math.ceil(layerProgress * this.layers[layer-1].length);
            slideMove = {layer: layer-1, index: downIndex};
            while(!this.isLegal(slideMove.layer, slideMove.index, color)) {
                slideMove = this.nextMove(color, slideMove.layer, slideMove.index);
            }
            this.layers[slideMove.layer][slideMove.index].monkeys.push(color);
            slideMove.subindex = this.layers[slideMove.layer][slideMove.index].monkeys.length;
        } else {
            layerProgress = index / this.layers[layer].length;
            downIndex = Math.floor(layerProgress * this.layers[layer-1].length);
            slideMove = {layer: layer-1, index: downIndex};
            while(!this.isLegal(slideMove.layer, slideMove.index, color)) {
                slideMove = this.prevMove(color, slideMove.layer, slideMove.index);
            }
            this.layers[slideMove.layer][slideMove.index].monkeys.push(color);
            slideMove.subindex = this.layers[slideMove.layer][slideMove.index].monkeys.length;
        }
        this.layers[layer][index].monkeys.splice(monkeyIndex, 1);
        return slideMove;
    },

    getRingSize: function (index) {
        return this.layers[index].length;
    },

    getSubindex: function(layer, index) {
        if(this.layers[layer][index].monkeys.length <= this.maxStack) {
            return this.layers[layer][index].monkeys.length;
        }
        return -1; // not allowed
    },

    startMove: function(color) {
        var index = color * Math.round(this.layers[0].length / this.playerCount);
        var layer = 0;
        return {
            layer: layer,
            index: index,
            subindex: this.getSubindex(layer, index)
        };
    },

    nextMove: function(color, layer, index) {
        if(layer === -1) {
            return this.startMove(color);
        } else if(layer === this.layers.length-1) {
            return false;
        } else if(layer === 6) {
            return false;
        }
        var next_layer = this.layers[layer][index].slideUp === color ? layer + 1 : layer;
        var next_index = this.layers[layer][index].slideUp === color ? this.layers[layer+1].findIndex(function(place) {
            return place.slideDown === color;
        }) : (index + 1) % this.layers[layer].length;
        var next_spot = {
            layer: next_layer,
            index: next_index,
            subindex: this.getSubindex(next_layer, next_index)
        };
        return next_spot;
    },

    prevMove: function(color, layer, index) {
        var start_move = this.startMove(color);
        if(layer === start_move.layer && index === start_move.index) {
            return {layer: -1, index: -1};
        }
        return {
            layer: this.layers[layer][index].slideDown === color ? layer - 1 : layer,
            index: this.layers[layer][index].slideDown === color ? this.layers[layer-1].findIndex(function(place) {
                return place.slideUp === color;
            }) : (index - 1) % this.layers[layer].length
        };
    },

    moveTarget: function(color, layer, index, distance) {
        var prev = {layer: layer, index: index};
        var next = prev;
        for(var i = 0; i < distance; i++) {
            next = this.nextMove(color, prev.layer, prev.index);
            if(!next) { return false; }
            prev = next;
        }
        return next;
    },

    cascadeBumps: function() {
        var bumps = [];
        this.layers.forEach(function(layer, i) {
            layer.forEach(function (position, j) {
                if(position.monkeys.length > this.maxStack) {
                    position.monkeys.sort();
                    var monkey_counts = new Array(this.playerCount);
                    monkey_counts.fill(0, 0, this.playerCount);
                    position.monkeys.forEach(function(color) {
                        monkey_counts[color]++;
                    }, this);

                    var minority_monkey_index = monkey_counts.findIndex(function(cnt) { return cnt !== 0; });
                    for(var k = 0; k < monkey_counts.length; k++) {
                        if(monkey_counts[k] > 0 && monkey_counts[k] < monkey_counts[minority_monkey_index]) {
                            minority_monkey_index = k;
                        }
                    }
                    var after_bump = this.slideDown(minority_monkey_index, i, j);
                    if(after_bump) {
                        bumps = bumps.concat({
                            layer: i,
                            index: j,
                            color: minority_monkey_index,
                            after_bump: after_bump
                        });
                    }
                }
            }, this);
        }, this);
        if(bumps.length > 0) {
            bumps = bumps.concat(this.cascadeBumps());
        }
        return bumps;
    }

}