function generate_moves(dice_count) {
    moves = [];
    for (var i = 0; i < dice_count; i++) {
        moves[i] = Math.floor(Math.random() * 5) + 1;
    }
    return moves;
}

var Board = function (player_count, monkey_count, max_stack) {

    this.layers = [[], [], [], [], [], [], [{monkeys: []}]];
    this.monkey_starts = [];
    this.max_stack = max_stack;
    this.current_player = 0;
    this.turn_index = 0;
    this.path_length = 0;
    this.player_count = player_count;

    for (var i = 0; i < player_count; i++) {

        this.monkey_starts[i] = monkey_count;
        this.layers[0] =
            this.layers[0].concat([{monkeys: [], slide_down: i},
                {monkeys: []},
                {monkeys: []},
                {monkeys: [], slide_up: (i + 1) % player_count}]);
        this.layers[1] =
            this.layers[1].concat([{monkeys: [], banana_card: true, slide_down: i},
                {monkeys: []},
                {monkeys: []},
                {monkeys: [], slide_up: (i + 1) % player_count}]);
        this.layers[2] =
            this.layers[2].concat([{monkeys: [], slide_down: i},
                {monkeys: []},
                {monkeys: [], slide_up: (i + 1) % player_count, banana_card: true}]);
        this.layers[3] =
            this.layers[3].concat([{monkeys: [], slide_down: i},
                {monkeys: []},
                {monkeys: [], slide_up: (i + 1) % player_count}]);
        this.layers[4] =
            this.layers[4].concat([{monkeys: [], slide_down: i},
                {monkeys: []},
                {monkeys: [], slide_up: (i + 1) % player_count}]);
        this.layers[5] =
            this.layers[5].concat([{monkeys: [], slide_down: i},
                {monkeys: [], slide_up: (i + 1) % player_count}]);
        this.path_length += 19;

    }

}

Board.prototype = {

    next_turn: function() {
        this.current_player = (this.current_player + 1) % this.player_count;
    },

    is_legal: function (layer, position, color) {
        if(this.layers[layer][position].monkeys.length === this.max_stack) {
            return this.layers[layer][position].monkeys.filter(function(monkey) {
                    return monkey === color;
                }).length >= Math.floor(this.max_stack/2);
        } else {
            return true;
        }
    },

    can_bump: function (layer, position, color) {
        var other_monkeys = this.layers[layer][position].monkeys.filter(function (x) {
            return x != color;
        });
        return other_monkeys.length == 1;
    },

    slide_down: function (color, layer, index) {
        var monkeyIndex = this.layers[layer][index].monkeys.indexOf(color);
        if(monkeyIndex === -1) {
            return false;
        }
        if(layer === 0) {
            var slideMove = {layer: -1, index: -1, subindex: -1};
            this.monkey_starts[color]++;
        } else if(layer < 5) {
            var layerProgress = index / this.layers[layer].length;
            var downIndex = Math.ceil(layerProgress * this.layers[layer-1].length);
            slideMove = {layer: layer-1, index: downIndex};
            while(!this.is_legal(slideMove.layer, slideMove.index, color)) {
                slideMove = this.next_move(color, slideMove.layer, slideMove.index);
            }
            this.layers[slideMove.layer][slideMove.index].monkeys.push(color);
            slideMove.subindex = this.layers[slideMove.layer][slideMove.index].monkeys.length;
        } else {
            layerProgress = index / this.layers[layer].length;
            downIndex = Math.floor(layerProgress * this.layers[layer-1].length);
            slideMove = {layer: layer-1, index: downIndex};
            while(!this.is_legal(slideMove.layer, slideMove.index, color)) {
                slideMove = this.prev_move(color, slideMove.layer, slideMove.index);
            }
            this.layers[slideMove.layer][slideMove.index].monkeys.push(color);
            slideMove.subindex = this.layers[slideMove.layer][slideMove.index].monkeys.length;
        }
        this.layers[layer][index].monkeys.splice(monkeyIndex, 1);
        return slideMove;
    },

    get_ring_size: function (index) {
        return this.layers[index].length;
    },

    get_subindex: function(layer, index) {
        if(this.layers[layer][index].monkeys.length <= this.max_stack) {
            return this.layers[layer][index].monkeys.length;
        }
        return -1; // not allowed
    },

    start_move: function(color) {
        var index = color * Math.round(this.layers[0].length / this.player_count);
        var layer = 0;
        return {
            layer: layer,
            index: index,
            subindex: this.get_subindex(layer, index)
        };
    },

    next_move: function(color, layer, index) {
        if(layer === -1) {
            return this.start_move(color);
        } else if(layer === this.layers.length-1) {
            return false;
        }
        var next_layer = this.layers[layer][index].slide_up === color ? layer + 1 : layer;
        var next_index = this.layers[layer][index].slide_up === color ? this.layers[layer+1].findIndex(function(place) {
            return place.slide_down === color;
        }) : (index + 1) % this.layers[layer].length;
        var next_spot = {
            layer: next_layer,
            index: next_index,
            subindex: this.get_subindex(next_layer, next_index)
        };
        return next_spot;
    },

    prev_move: function(color, layer, index) {
        var start_move = this.start_move(color);
        if(layer === start_move.layer && index === start_move.index) {
            return {layer: -1, index: -1};
        }
        return {
            layer: this.layers[layer][index].slide_down === color ? layer - 1 : layer,
            index: this.layers[layer][index].slide_down === color ? this.layers[layer-1].findIndex(function(place) {
                return place.slide_up === color;
            }) : (index - 1) % this.layers[layer].length
        };
    },

    move_target: function(color, layer, index, distance) {
        var prev = {layer: layer, index: index};
        var next = prev;
        for(var i = 0; i < distance; i++) {
            next = this.next_move(color, prev.layer, prev.index);
            if(!next) { return false; }
            prev = next;
        }
        return next;
    },

    cascade_bumps: function() {
        var bumps = [];
        this.layers.forEach(function(layer, i) {
            layer.forEach(function (position, j) {
                if(position.monkeys.length > this.max_stack) {
                    position.monkeys.sort();
                    var monkey_counts = new Array(this.player_count);
                    monkey_counts.fill(0, 0, this.player_count);
                    position.monkeys.forEach(function(color) {
                        monkey_counts[color]++;
                    }, this);

                    var minority_monkey_index = monkey_counts.findIndex(function(cnt) { return cnt !== 0; });
                    for(var k = 0; k < monkey_counts.length; k++) {
                        if(monkey_counts[k] > 0 && monkey_counts[k] < monkey_counts[minority_monkey_index]) {
                            minority_monkey_index = k;
                        }
                    }
                    var after_bump = this.slide_down(minority_monkey_index, i, j);
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
            bumps = bumps.concat(this.cascade_bumps());
        }
        return bumps;
    }

}