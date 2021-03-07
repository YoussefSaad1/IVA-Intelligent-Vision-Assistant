import requests 
import numpy as np
import json
import base64
from io import BytesIO
from PIL import Image


def decode(base64_string):
    image = Image.open(BytesIO(base64.b64decode(base64_string)))
    image = image.convert("RGB")
    img = np.array(image)

    return np.array(img)


URL = "http://127.0.0.1:5000/emotion"


f = open('emotion_test.jpg', 'rb').read()
f = str(base64.b64encode(f))


f = f[2:len(f)-1]

data = {"image": f}

headers = {'content-type': 'application/json'}

r = requests.post(url=URL, json=json.dumps(data))

print(r)

data = r.json()

with open('emotion_output.txt', 'w', encoding='utf-8') as nf:
    nf.write(data['text'])


print(data['text'])

sound = data['sound']

if len(sound) > 30: 
    # Meaning credentials were found, base64 encoded sound will be more than 30 characters
    # Check read_text method in utils
    sound = base64.b64decode(sound)

    with open('emotion_output.mp3', 'wb') as nf:
        nf.write(sound)
