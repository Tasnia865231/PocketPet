import json, re

LINES = [(0,1,2),(3,4,5),(6,7,8),(0,3,6),(1,4,7),(2,5,8),(0,4,8),(2,4,6)]
def nv(k): return "newData.child('%s').val()" % k
def dv(k): return "data.child('%s').val()" % k
def nc(i): return "newData.child('board').child('%d').val()" % i
def oc(i): return "data.child('board').child('%d').val()" % i
def AND(*a): return "(" + " && ".join(a) + ")"
def OR(*a):  return "(" + " || ".join(a) + ")"
def cnt(f, s): return "(" + " + ".join("(%s === '%s' ? 1 : 0)" % (f(i), s) for i in range(9)) + ")"
def has(s): return OR(*[AND(*["%s === '%s'" % (nc(i), s) for i in l]) for l in LINES])

FULL    = AND(*["%s !== null" % nc(i) for i in range(9)])
HASX, HASO = has('X'), has('O')
OUTCOME = "(%s ? 'X' : (%s ? 'O' : 'D'))" % (HASX, HASO)       # only meaningful when the round is over
ROUND_OVER = OR(HASX, HASO, FULL)
KEEP_CELLS = AND(*["(%s === null || %s === %s)" % (oc(i), nc(i), oc(i)) for i in range(9)])
SAME_CORE  = AND(*["%s === %s" % (nv(k), dv(k)) for k in ['x','o','xName','oName','createdAt','round']])
ALL_RES_SAME = AND(*["%s === %s" % (nv('r%d'%k), dv('r%d'%k)) for k in (1,2,3)])
OTHER_RES_SAME = AND(*["(%s === %d || %s === %s)" % (dv('round'), k, nv('r%d'%k), dv('r%d'%k)) for k in (1,2,3)])
NEW_RES = "(%s === 1 ? %s : (%s === 2 ? %s : %s))" % (dv('round'), nv('r1'), dv('round'), nv('r2'), nv('r3'))
MOVER = OR(AND("%s === 'X'" % dv('turn'), "%s === auth.uid" % dv('x')),
           AND("%s === 'O'" % dv('turn'), "%s === auth.uid" % dv('o')))
NEW_SX = "(%s + (%s === 'X' ? 1 : 0))" % (dv('sx'), OUTCOME)
NEW_SO = "(%s + (%s === 'O' ? 1 : 0))" % (dv('so'), OUTCOME)
WINNER_OK = "%s === (%s > %s ? 'X' : (%s > %s ? 'O' : 'D'))" % (nv('winner'), NEW_SX, NEW_SO, NEW_SO, NEW_SX)

PLAYER_OLD = OR("%s === auth.uid" % dv('x'), "%s === auth.uid" % dv('o'))
INV = lambda f: "root.child('invites').child(auth.uid).child(%s).child('%s').val()" % (nv('x'), f)

CREATE = AND("!data.exists()", "%s === auth.uid" % nv('o'), "%s !== auth.uid" % nv('x'),
  "%s === 'accepted'" % INV('state'), "%s === $gid" % INV('gameId'), "%s > now - 90000" % INV('createdAt'),
  "!root.child('playing').child(auth.uid).exists()",
  "%s === 'playing'" % nv('phase'), "%s === 1" % nv('round'), "%s === 'X'" % nv('turn'),
  "%s === 0" % nv('sx'), "%s === 0" % nv('so'),
  "!newData.hasChild('board')", "!newData.hasChild('r1')", "!newData.hasChild('r2')", "!newData.hasChild('r3')",
  "!newData.hasChild('winner')", "!newData.hasChild('endedAt')", "!newData.hasChild('cancelledBy')",
  "%s === now" % nv('createdAt'))

MOVE_CONTINUES = AND("%s === 'playing'" % nv('phase'), "%s === (%s === 'X' ? 'O' : 'X')" % (nv('turn'), dv('turn')),
  "%s === %s" % (nv('sx'), dv('sx')), "%s === %s" % (nv('so'), dv('so')), ALL_RES_SAME,
  "!newData.hasChild('winner')", "%s === %s" % (nv('endedAt'), dv('endedAt')))
MOVE_ENDS = AND("%s === now" % nv('endedAt'), "%s === %s" % (nv('turn'), dv('turn')), OTHER_RES_SAME,
  "%s === %s" % (NEW_RES, OUTCOME),
  "%s === %s" % (nv('sx'), NEW_SX), "%s === %s" % (nv('so'), NEW_SO),
  "(%s < 3 ? (%s === 'roundEnd' && !newData.hasChild('winner')) : (%s === 'finished' && %s))"
     % (dv('round'), nv('phase'), nv('phase'), WINNER_OK))
MOVE = AND("%s === 'playing'" % dv('phase'), SAME_CORE, MOVER, KEEP_CELLS,
  "%s === %s + (%s === 'X' ? 1 : 0)" % (cnt(nc,'X'), cnt(oc,'X'), dv('turn')),
  "%s === %s + (%s === 'O' ? 1 : 0)" % (cnt(nc,'O'), cnt(oc,'O'), dv('turn')),
  "(%s ? %s : %s)" % (ROUND_OVER, MOVE_ENDS, MOVE_CONTINUES))

ADVANCE = AND("%s === 'roundEnd'" % dv('phase'), "%s === 'playing'" % nv('phase'),
  "%s === %s + 1" % (nv('round'), dv('round')), "!newData.hasChild('board')", "%s === 'X'" % nv('turn'),
  "now > %s + 2500" % dv('endedAt'),
  AND(*["%s === %s" % (nv(k), dv(k)) for k in ['x','o','xName','oName','createdAt','sx','so','endedAt','r1','r2','r3']]),
  "!newData.hasChild('winner')")

CANCEL = AND(OR("%s === 'playing'" % dv('phase'), "%s === 'roundEnd'" % dv('phase')),
  "%s === 'cancelled'" % nv('phase'), "%s === auth.uid" % nv('cancelledBy'),
  AND(*["%s === %s" % (nv(k), dv(k)) for k in ['x','o','xName','oName','createdAt','round','sx','so']]))

GAME_VALIDATE = AND("newData.hasChildren(['x','o','xName','oName','phase','round','turn','sx','so','createdAt'])",
                    OR(CREATE, MOVE, ADVANCE, CANCEL))

def cc(v): return {".validate": v}
rules_games = {
  "$gid": {
    ".read": "auth != null && " + PLAYER_OLD,
    ".write": "auth != null && newData.exists() && (!data.exists() || %s)" % PLAYER_OLD,
    ".validate": GAME_VALIDATE,
    "x": cc("newData.isString()"), "o": cc("newData.isString()"),
    "xName": cc("newData.isString() && newData.val().length >= 1 && newData.val().length <= 16"),
    "oName": cc("newData.isString() && newData.val().length >= 1 && newData.val().length <= 16"),
    "phase": cc("newData.val() === 'playing' || newData.val() === 'roundEnd' || newData.val() === 'finished' || newData.val() === 'cancelled'"),
    "round": cc("newData.isNumber() && newData.val() >= 1 && newData.val() <= 3"),
    "turn": cc("newData.val() === 'X' || newData.val() === 'O'"),
    "board": {"$cell": cc("(newData.val() === 'X' || newData.val() === 'O') && $cell.matches(/^[0-8]$/)")},
    "r1": cc("newData.val() === 'X' || newData.val() === 'O' || newData.val() === 'D'"),
    "r2": cc("newData.val() === 'X' || newData.val() === 'O' || newData.val() === 'D'"),
    "r3": cc("newData.val() === 'X' || newData.val() === 'O' || newData.val() === 'D'"),
    "sx": cc("newData.isNumber() && newData.val() >= 0 && newData.val() <= 3"),
    "so": cc("newData.isNumber() && newData.val() >= 0 && newData.val() <= 3"),
    "winner": cc("newData.val() === 'X' || newData.val() === 'O' || newData.val() === 'D'"),
    "endedAt": cc("newData.isNumber()"),
    "createdAt": cc("newData.isNumber()"),
    "cancelledBy": cc("newData.isString()"),
    "$other": cc("false"),
  }
}

IN_ = lambda f: "root.child('invites').child($to).child($from).child('%s').val()" % f
INV_WRITE = ("auth != null && ("
  "(auth.uid === $from && $from !== $to && newData.exists() && newData.child('state').val() === 'pending'"
     " && (!data.exists() || data.child('state').val() !== 'pending' || data.child('createdAt').val() < now - 30000)"
     " && root.child('status').child($to).child('state').val() === 'online'"
     " && !root.child('playing').child($to).exists() && !root.child('playing').child($from).exists())"
  " || (auth.uid === $from && !newData.exists())"
  " || (auth.uid === $to && !newData.exists())"
  " || (auth.uid === $to && newData.exists() && data.child('state').val() === 'pending'"
     " && data.child('createdAt').val() > now - 32000"
     " && (newData.child('state').val() === 'accepted' || newData.child('state').val() === 'rejected')))")
INV_VALIDATE = ("newData.hasChildren(['fromName','toName','state','createdAt']) && ("
  "(newData.child('state').val() === 'pending' && newData.child('createdAt').val() === now && !newData.hasChild('gameId'))"
  " || (data.exists() && newData.child('createdAt').val() === data.child('createdAt').val()"
     " && newData.child('fromName').val() === data.child('fromName').val()"
     " && newData.child('toName').val() === data.child('toName').val()"
     " && ((newData.child('state').val() === 'rejected' && !newData.hasChild('gameId'))"
        " || (newData.child('state').val() === 'accepted' && newData.child('gameId').isString()))))")

def other_claims(): return "root.child('games').child($gid)"
G = other_claims()
CLAIM_WRITE = ("auth != null && auth.uid === $uid && !data.exists() && newData.val() === true"
  " && %s.child('phase').val() === 'finished'"
  " && ((%s.child('winner').val() === 'X' && %s.child('x').val() === $uid)"
  " || (%s.child('winner').val() === 'O' && %s.child('o').val() === $uid))") % (G, G, G, G, G)

# ---- chat: chats/{gameId}/{messageId}  (only the two players of games/{gameId}; messages are create-only)
CHAT_PLAYER = OR("root.child('games').child($gid).child('x').val() === auth.uid",
                 "root.child('games').child($gid).child('o').val() === auth.uid")
rules_chats = {
  "$gid": {
    ".read": "auth != null && " + CHAT_PLAYER,
    ".write": "auth != null && !newData.exists() && " + CHAT_PLAYER,      # a player may delete the whole chat
    ".indexOn": ["createdAt"],
    "$mid": {
      ".write": ("auth != null && !data.exists() && newData.exists() && " + CHAT_PLAYER
                 + " && root.child('games').child($gid).child('phase').val() !== 'cancelled'"),
      ".validate": "newData.hasChildren(['uid', 'text', 'createdAt']) && $mid.length <= 40",
      "uid": cc("newData.val() === auth.uid"),
      "text": cc("newData.isString() && newData.val().length >= 1 && newData.val().length <= 60 && newData.val().matches(/\\S/)"),
      "createdAt": cc("newData.isNumber() && newData.val() === now"),
      "$other": cc("false"),
    }
  }
}

rules = {
  "rules": {
    ".read": False, ".write": False,
    "status": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null && auth.uid === $uid",
        ".validate": "newData.hasChildren(['state', 'lastChanged', 'displayName'])",
        "state": {".validate": "newData.val() === 'online' || newData.val() === 'offline'"},
        "lastChanged": {".validate": "newData.isNumber() && newData.val() === now"},
        "displayName": {".validate": "newData.isString() && newData.val().length >= 1 && newData.val().length <= 16"},
        "$other": {".validate": False}
      }
    },
    "activities": {
      ".read": "auth != null",
      ".indexOn": ["createdAt"],
      "$eventId": {
        ".write": "auth != null && !data.exists() && newData.child('uid').val() === auth.uid",
        ".validate": "newData.hasChildren(['uid', 'type', 'value', 'createdAt']) && (newData.child('type').val() !== 'minigame' || newData.hasChild('coins'))",
        "uid": {".validate": "newData.val() === auth.uid"},
        "type": {".validate": "newData.val() === 'level' || newData.val() === 'minigame'"},
        "value": {".validate": "newData.isNumber() && newData.val() >= 1 && newData.val() <= 100000 && (newData.parent().child('type').val() !== 'level' || newData.val() <= 10) && (newData.parent().child('type').val() !== 'minigame' || newData.val() <= 50)"},
        "coins": {".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100000"},
        "createdAt": {".validate": "newData.val() === now"},
        "$other": {".validate": False}
      }
    },
    "invites": {
      "$to": {
        ".read": "auth != null && auth.uid === $to",
        "$from": {
          ".read": "auth != null && (auth.uid === $to || auth.uid === $from)",
          ".write": INV_WRITE,
          ".validate": INV_VALIDATE,
          "fromName": cc("newData.isString() && newData.val().length >= 1 && newData.val().length <= 16"),
          "toName": cc("newData.isString() && newData.val().length >= 1 && newData.val().length <= 16"),
          "state": cc("newData.val() === 'pending' || newData.val() === 'accepted' || newData.val() === 'rejected'"),
          "createdAt": cc("newData.isNumber()"),
          "gameId": cc("newData.isString() && newData.val().length <= 40"),
          "$other": cc("false"),
        }
      }
    },
    "playing": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid",
        ".validate": "newData.isString() && newData.val().length <= 40"
      }
    },
    "games": rules_games,
    "chats": rules_chats,
    "claims": {
      "$gid": {
        "$uid": {
          ".read": "auth != null && auth.uid === $uid",
          ".write": CLAIM_WRITE,
          ".validate": "newData.val() === true"
        }
      }
    }
  }
}
json.dump(rules, open('../database.rules.json','w'), indent=2)
print(len(json.dumps(rules)))
