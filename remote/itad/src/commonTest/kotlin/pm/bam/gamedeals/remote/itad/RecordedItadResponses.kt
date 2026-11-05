package pm.bam.gamedeals.remote.itad

// Real IsThereAnyDeal replies recorded on 5 Nov 2026 (trimmed), so tests check the service's actual shape and
// behaviour rather than our assumptions about it. Re-record if the API changes.

/** `POST /games/overview/v2?country=GB&vouchers=true` for GTA V, GTA San Andreas, Elden Ring and Eye of the Beholder. Only Elden Ring comes back: ITAD leaves out games with no price data in the region. */
internal const val RECORDED_OVERVIEW_GB = """
{
 "prices": [
  {
   "id": "018d937f-590c-728b-ac35-38bcff85f086",
   "current": {
    "shop": {
     "id": 73,
     "name": "PlanetPlay"
    },
    "price": {
     "amount": 42.21,
     "amountInt": 4221,
     "currency": "GBP"
    },
    "regular": {
     "amount": 51.48,
     "amountInt": 5148,
     "currency": "GBP"
    },
    "cut": 18,
    "voucher": "AUTUMN",
    "flag": null,
    "drm": [
     {
      "id": 61,
      "name": "Steam"
     }
    ],
    "platforms": [
     {
      "id": 1,
      "name": "Windows"
     }
    ],
    "timestamp": "2026-10-01T05:50:33+02:00",
    "expiry": null,
    "url": "https://itad.link/019852bb-6ebe-70b6-be9c-396adb014988/?app=jvku76"
   },
   "lowest": {
    "shop": {
     "id": 65,
     "name": "JoyBuggy"
    },
    "price": {
     "amount": 24.75,
     "amountInt": 2475,
     "currency": "GBP"
    },
    "regular": {
     "amount": 49.99,
     "amountInt": 4999,
     "currency": "GBP"
    },
    "cut": 50,
    "timestamp": "2023-12-21T19:01:57+01:00"
   },
   "bundled": 0,
   "urls": {
    "game": "https://isthereanydeal.com/game/id:018d937f-590c-728b-ac35-38bcff85f086/"
   }
  }
 ]
}
"""

/** `POST /deals/v2` with the guide's body (GB, sort rank, games only), first three of 100. */
internal const val RECORDED_POPULAR_GB = """
{
 "nextOffset": 100,
 "hasMore": true,
 "list": [
  {
   "id": "018d937f-2997-7131-b8b9-7c8af4825fa8",
   "slug": "cyberpunk-2077",
   "title": "Cyberpunk 2077",
   "type": "game",
   "mature": false,
   "assets": {
    "boxart": "https://assets.isthereanydeal.com/018d937f-2997-7131-b8b9-7c8af4825fa8/boxart.jpg?t=1792687867",
    "banner300": "https://assets.isthereanydeal.com/018d937f-2997-7131-b8b9-7c8af4825fa8/banner300.jpg?t=1792687867"
   },
   "deal": {
    "shop": {
     "id": 77,
     "name": "Muve"
    },
    "price": {
     "amount": 14.99,
     "amountInt": 1499,
     "currency": "GBP"
    },
    "regular": {
     "amount": 49.99,
     "amountInt": 4999,
     "currency": "GBP"
    },
    "cut": 70,
    "voucher": null,
    "storeLow": {
     "amount": 14.99,
     "amountInt": 1499,
     "currency": "GBP"
    },
    "historyLow": {
     "amount": 14.99,
     "amountInt": 1499,
     "currency": "GBP"
    },
    "historyLow_1y": {
     "amount": 14.99,
     "amountInt": 1499,
     "currency": "GBP"
    },
    "historyLow_3m": {
     "amount": 14.99,
     "amountInt": 1499,
     "currency": "GBP"
    },
    "flag": "H",
    "drm": [
     {
      "id": 35,
      "name": "GOG"
     }
    ],
    "platforms": [
     {
      "id": 1,
      "name": "Windows"
     }
    ],
    "timestamp": "2026-10-29T20:15:28+01:00",
    "expiry": null,
    "url": "https://itad.link/019eb6e4-ffc0-7174-a2a0-b6864bbb96de/?app=jvku76"
   }
  },
  {
   "id": "018d937f-590c-728b-ac35-38bcff85f086",
   "slug": "elden-ring",
   "title": "Elden Ring",
   "type": "game",
   "mature": false,
   "assets": {
    "boxart": "https://assets.isthereanydeal.com/018d937f-590c-728b-ac35-38bcff85f086/boxart.jpg?t=1790295614",
    "banner300": "https://assets.isthereanydeal.com/018d937f-590c-728b-ac35-38bcff85f086/banner300.jpg?t=1790295614"
   },
   "deal": {
    "shop": {
     "id": 28,
     "name": "GamesPlanet FR"
    },
    "price": {
     "amount": 44.99,
     "amountInt": 4499,
     "currency": "GBP"
    },
    "regular": {
     "amount": 49.99,
     "amountInt": 4999,
     "currency": "GBP"
    },
    "cut": 10,
    "voucher": null,
    "storeLow": {
     "amount": 27.27,
     "amountInt": 2727,
     "currency": "GBP"
    },
    "historyLow": {
     "amount": 24.75,
     "amountInt": 2475,
     "currency": "GBP"
    },
    "historyLow_1y": {
     "amount": 28.55,
     "amountInt": 2855,
     "currency": "GBP"
    },
    "historyLow_3m": {
     "amount": 40.28,
     "amountInt": 4028,
     "currency": "GBP"
    },
    "flag": null,
    "drm": [
     {
      "id": 61,
      "name": "Steam"
     }
    ],
    "platforms": [
     {
      "id": 1,
      "name": "Windows"
     }
    ],
    "timestamp": "2026-03-26T18:03:35+01:00",
    "expiry": null,
    "url": "https://itad.link/018d9386-5633-7086-a2fa-f903b0bdf0db/?app=jvku76"
   }
  },
  {
   "id": "018d937f-3a3b-7210-bd2d-0d1dfb1d84c0",
   "slug": "red-dead-redemption-2",
   "title": "Red Dead Redemption 2",
   "type": "game",
   "mature": false,
   "assets": {
    "boxart": "https://assets.isthereanydeal.com/018d937f-3a3b-7210-bd2d-0d1dfb1d84c0/boxart.jpg?t=1787681123",
    "banner300": "https://assets.isthereanydeal.com/018d937f-3a3b-7210-bd2d-0d1dfb1d84c0/banner300.jpg?t=1787681123"
   },
   "deal": {
    "shop": {
     "id": 36,
     "name": "GreenManGaming"
    },
    "price": {
     "amount": 13.5,
     "amountInt": 1350,
     "currency": "GBP"
    },
    "regular": {
     "amount": 59.99,
     "amountInt": 5999,
     "currency": "GBP"
    },
    "cut": 77,
    "voucher": null,
    "storeLow": {
     "amount": 12.9,
     "amountInt": 1290,
     "currency": "GBP"
    },
    "historyLow": {
     "amount": 10.55,
     "amountInt": 1055,
     "currency": "GBP"
    },
    "historyLow_1y": {
     "amount": 10.99,
     "amountInt": 1099,
     "currency": "GBP"
    },
    "historyLow_3m": {
     "amount": 10.99,
     "amountInt": 1099,
     "currency": "GBP"
    },
    "flag": null,
    "drm": [
     {
      "id": 16,
      "name": "Epic"
     }
    ],
    "platforms": [
     {
      "id": 1,
      "name": "Windows"
     }
    ],
    "timestamp": "2026-10-23T10:26:29+02:00",
    "expiry": null,
    "url": "https://itad.link/018d9386-b703-7302-9857-11d2d1d13c48/?app=jvku76"
   }
  }
 ]
}
"""
