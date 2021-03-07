from flask import Flask, jsonify, request
import base64
import json
import numpy as np
import tensorflow as tf
from keras.preprocessing import image
from keras.preprocessing.text import tokenizer_from_json
import json
import pickle
from caption_utils import *
from utils import *
import random
import os

application = Flask(__name__)


@application.route("/")
def index():
    return "Hello!\n Welcome to IVA"

# The general method used among routes is that the api gets a request with a base64 encoded image in the 'image'
# label, then we decode it, obtain the result, get the sound output, then decode it with base64 to be returned
# in json with label 'sound'.

@application.route("/ocr", methods=["POST"])
def ocr():
    read = request.get_json()
    if type(read) == str:
        read = json.loads(read)

    img = read['image']
    data = {}
    img = base64.b64decode(img)
    res = detect_text(img)
    data['text'] = res

    if len(data['text']) < 2:
        data['sound'] = ""
    else:
        data['sound'] = read_text(data['text'])

    return jsonify(data)



@application.route("/vqa", methods=["POST"])
def vqa():
    read = request.get_json()
    if type(read) == str:
        read = json.loads(read)
    img = read['image']

    img_name = 'image' + str(random.randint(1,1001)) + '.jpg'
    with open(img_name, "wb") as fh:
        fh.write(base64.b64decode(img))

    with open('vqa/tokenizer2.json') as f:
        data = json.load(f)
        vqa_ques_tokenizer = tokenizer_from_json(data)

    vqa_model = tf.keras.models.load_model('vqa/vqa_model.h5')
    vqa_image_model = tf.keras.applications.xception.Xception(weights='imagenet', include_top=False)

    topAnsIndexWord = pickle.load(open('vqa/topAnsIndexWord2.pkl', 'rb'))

    img = image.load_img(img_name, target_size=(299, 299))
    os.remove(img_name)

    # Obtaining features from image using Xception model like the one used in training
    x = image.img_to_array(img)
    x = np.expand_dims(x, axis=0)
    x = tf.keras.applications.xception.preprocess_input(x)

    features = vqa_image_model.predict(x)
    X1 = features.reshape((1, 10*10, -1))

    # Cleaning the input question the same way used with the model
    ques = read['question']
    ques = clean_str(ques)
    X2 = vqa_ques_tokenizer.texts_to_sequences([ques])
    X2 = tf.keras.preprocessing.sequence.pad_sequences(X2, padding='post', truncating='post', maxlen=15)

    data = {}
    data['question'] = read['question']

    # Obtaining model prediction then converting it with the index-to-word mapper that was built with the model
    pred = vqa_model.predict([X1, X2])
    pred2 = pred[0].argsort()[-5:][::-1]
    data['answers'] = {}
    txt = ""
    for i in pred2:
        if pred[0][i] > 0.01:
            txt += topAnsIndexWord[i] + " بنسبة " + str(pred[0][i])[:4] + ". \n"
            data['answers'][topAnsIndexWord[i]] = str(pred[0][i])
            break
        else:
            txt = "عفوا، لا يمكنني الإجابة على هذا السؤال"
    data['text'] = txt
    data['sound'] = read_text(txt)
    return jsonify(data)


@application.route("/emotion", methods=["POST"])
def emotion():
    read = request.get_json()
    if type(read) == str:
        read = json.loads(read)
    img = read['image']
    img = decode(img)

    new_img, emotion = emotion_finder(img)

    if (emotion == []):
        emotion = "عفوا لا نستطيع اكتشاف وجوه، حاول مرة أخرى "
    else:
        emotion = " و ".join(emotion)
        emotion = "يبدو " + emotion

    data = {}
    data['sound'] = read_text(emotion)
    data['text'] = emotion

    return jsonify(data)


@application.route("/caption", methods=["POST"])
def caption():
    read = request.get_json()
    if type(read) == str:
        read = json.loads(read)
    img = read['image']
    img = decode(img)

    result = evaluate(img)
    result = ' '.join(result)
    result = " يبدو كأنه " + result
    data = {}
    result = filter_string(result)
    data['sound'] = read_text(result)
    data['text'] = result

    return jsonify(data)

@application.route("/currency", methods=["POST"])
def currency():
    read = request.get_json()
    if type(read) == str:
        read = json.loads(read)
    img = read['image']
    img = decode(img)

    result = Curr_Pred(img)
    data = {}
    data['text'] = result
    data['sound'] = read_text(result)

    return jsonify(data)
    
