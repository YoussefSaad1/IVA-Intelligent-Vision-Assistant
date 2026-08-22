# IVA — Intelligent Vision Assistant

**An Android assistant that describes the world to Arabic-speaking blind and low-vision users.**

Point the phone at what's in front of you; IVA identifies objects, recognises people you've saved,
reads their facial expression, and says it aloud — in Arabic.

Most assistive vision tools speak English. That makes them useless to a large part of the people who
need them most. IVA was built to close that gap.

[![Watch the demo](https://img.shields.io/badge/▶_Watch_the_demo-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://www.youtube.com/watch?v=z21FjWr9JDE)

---

## What it does

| Capability | What the user hears |
|---|---|
| **Object detection** | What's in front of them, and roughly where |
| **Face recognition** | Names a person they've previously saved |
| **Emotion recognition** | Whether that person is smiling, sad, surprised |
| **Arabic speech output** | All of it spoken in Arabic, not English |

Emotion recognition matters more than it sounds. A blind user in a conversation has no access to the
other person's expression — one of the largest channels of meaning in a face-to-face exchange.

## Architecture

```
┌──────────────────┐        ┌─────────────────────────┐
│  Android client  │───────►│  Inference API          │
│                  │        │                         │
│  • camera        │  image │  • object detection     │
│  • Arabic TTS    │◄───────│  • face recognition     │
│  • saved faces   │  label │  • emotion recognition  │
└──────────────────┘        └─────────────────────────┘
```

Inference runs server-side so the phone stays a thin client — important when the target device is
whatever handset the user already owns, not a flagship.

## Repo layout

| Path | What it is |
|---|---|
| `Android/` | The mobile client — camera capture, Arabic speech output |
| `API/` | Inference backend serving the vision models |
| `Notebooks/` | Model training and experimentation |
| `emotion_for_git.ipynb` | Emotion-recognition model training |
| `IVA-GPbook.pdf` | Full project book — problem framing, dataset choices, evaluation, results |

The [project book](IVA-GPbook.pdf) is the most complete write-up: why each model was chosen, how it
was evaluated, and what didn't work.

## Status

Graduation project, Mansoura University — Electronics & Communication Engineering. Archived as
built; the accessibility problem it targets is still very much open.

---

*Built by [Youssef Saad Abdul-Majid](https://github.com/YoussefSaad1). Still the project I'm most
fond of — it's the first thing I built that someone could actually use.*
