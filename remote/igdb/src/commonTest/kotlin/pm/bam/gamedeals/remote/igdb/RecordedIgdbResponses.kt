package pm.bam.gamedeals.remote.igdb

// A real IGDB reply recorded on 5 Nov 2026 (Steam store links only), so tests check the service's actual shape.
// Note it has no `category` field at all; games are classified by `game_type`. Re-record if the API changes.

/** `POST /v4/games` with the guide's franchise query for Elden Ring (franchise 8190, limit 30). */
internal const val RECORDED_ELDEN_RING_FRANCHISE = """
[
 {
  "id": 119133,
  "cover": {
   "id": 212094,
   "image_id": "co4jni"
  },
  "external_games": [
   {
    "id": 2084186,
    "name": "ELDEN RING",
    "uid": "1245620",
    "external_game_source": 1
   }
  ],
  "name": "Elden Ring",
  "themes": [
   {
    "id": 1
   },
   {
    "id": 17
   },
   {
    "id": 38
   }
  ],
  "game_type": {
   "id": 0,
   "type": "Main Game"
  }
 },
 {
  "id": 325591,
  "cover": {
   "id": 426980,
   "image_id": "co95gk"
  },
  "external_games": [
   {
    "id": 2969539,
    "name": "ELDEN RING NIGHTREIGN",
    "uid": "2622380",
    "external_game_source": 1
   }
  ],
  "name": "Elden Ring Nightreign",
  "themes": [
   {
    "id": 1
   },
   {
    "id": 17
   },
   {
    "id": 21
   },
   {
    "id": 38
   }
  ],
  "game_type": {
   "id": 0,
   "type": "Main Game"
  }
 },
 {
  "id": 338079,
  "cover": {
   "id": 570754,
   "image_id": "coc8ea"
  },
  "name": "Elden Ring: Tarnished Edition",
  "themes": [
   {
    "id": 17
   }
  ],
  "game_type": {
   "id": 3,
   "type": "Bundle"
  },
  "external_games": []
 },
 {
  "id": 415376,
  "cover": {
   "id": 596246,
   "image_id": "cocs2e"
  },
  "external_games": [
   {
    "id": 3210416,
    "name": "ELDEN RING Tarnished Pack",
    "uid": "3655690",
    "external_game_source": 1
   }
  ],
  "name": "Elden Ring: Tarnished Pack",
  "parent_game": 119133,
  "themes": [
   {
    "id": 1
   },
   {
    "id": 17
   },
   {
    "id": 38
   }
  ],
  "game_type": {
   "id": 1,
   "type": "DLC"
  }
 },
 {
  "id": 180258,
  "cover": {
   "id": 202467,
   "image_id": "co4c83"
  },
  "external_games": [],
  "name": "Elden Ring: Collector's Edition",
  "themes": [
   {
    "id": 1
   },
   {
    "id": 17
   },
   {
    "id": 38
   }
  ],
  "game_type": {
   "id": 0,
   "type": "Main Game"
  }
 }
]
"""
