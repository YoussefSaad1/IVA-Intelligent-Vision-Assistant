import base64
from google.cloud import vision
import os
import random
import re
from io import BytesIO
from PIL import Image
import cv2
import numpy as np
from tensorflow.keras.models import load_model
from keras.preprocessing.image import img_to_array
import imutils
import keras.backend.tensorflow_backend as tb
import tensorflow as tf

tb._SYMBOLIC_SCOPE.value = True

# In ocr folder, put your GCP project credentials with vision api enabled
os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = "ocr/YOUR_GCP_PROJECT_CREDENTIALS_FILE.json"

# Detecting text from image for OCR
def detect_text(content):
    try:
        client = vision.ImageAnnotatorClient()

        image = vision.types.Image(content=content)

        response = client.text_detection(image=image)
        texts = response.text_annotations
        ret = texts[0].description

        if response.error.message:
            raise Exception(
                '{}\nFor more info on error messages, check: '
                'https://cloud.google.com/apis/design/errors'.format(
                    response.error.message))

        return ret
    except:
        return "No credentials found"

# Using Text-To-Speech from GCP
def read_text(my_text):
    try:
        from google.cloud import texttospeech

        client = texttospeech.TextToSpeechClient()

        synthesis_input = texttospeech.types.SynthesisInput(text=my_text)

        voice = texttospeech.types.VoiceSelectionParams(
            language_code='ar',
            ssml_gender=texttospeech.enums.SsmlVoiceGender.FEMALE)

        audio_config = texttospeech.types.AudioConfig(
            audio_encoding=texttospeech.enums.AudioEncoding.MP3)

        response = client.synthesize_speech(synthesis_input, voice, audio_config)

        out_name = 'output' + str(random.randint(1, 1001)) + '.mp3'
        with open(out_name, 'wb') as out:
            out.write(response.audio_content)

        sound_bytes = open(out_name, "rb").read()
        os.remove(out_name)

        en = str(base64.b64encode(sound_bytes))
        en = en[2:len(en)-1]
        return en

    except:
        return "No credentials found"

   

# Same clean_str method used for training vqa model to unify input shape
def clean_str(text):
    search = ["أ","إ","آ","ة","_","-","/",".","،"," و "," يا ",'"',"ـ","'","ى","\\",'\n', '\t','&quot;','?','؟','!']
    replace = ["ا","ا","ا","ه"," "," ","","",""," و"," يا","","","","ي","",' ', ' ',' ',' ? ',' ؟ ',' ! ']
    #remove tashkeel
    p_tashkeel = re.compile(r'[\u0617-\u061A\u064B-\u0652]')
    text = re.sub(p_tashkeel, "", text)
    #remove longation
    p_longation = re.compile(r'(.)\1+')
    subst = r"\1\1"
    text = re.sub(p_longation, subst, text)
    text = text.replace('وو', 'و')
    text = text.replace('يي', 'ي')
    text = text.replace('اا', 'ا')
    for i in range(0, len(search)):
        text = text.replace(search[i], replace[i])
    #trim    
    text = text.strip()
    text = str(text)
    text = re.sub(r'\bال(\w\w+)', r'\1', text)

    return text


def encode(img):
    encoded_string = base64.b64encode(img)
    return encoded_string


def decode(base64_string):
    image = Image.open(BytesIO(base64.b64decode(base64_string)))
    image = image.convert("RGB")

    return np.array(image)


def emotion_finder(img):
    # parameters for loading data and images
    detection_model_path = 'emotion/haarcascade_frontalface_default.xml'
    emotion_model_path = 'emotion/gpu_mini_XCEPTION.63-0.64.hdf5'

    # hyper-parameters for bounding boxes shape
    # loading models
    face_detection = cv2.CascadeClassifier(detection_model_path)
    emotion_classifier = load_model(emotion_model_path, compile=False)
    EMOTIONS = ["angry" ,"disgust","scared", "happy", "sad", "surprised","neutral"]
    EMOTIONS_ar = ["غاضب" ,"مشمئز","خائف", "سعيد", "حزين", "متفاجيء","طبيعي"]

    img = imutils.resize(img,width=400)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    #to get faces from image 
    faces = face_detection.detectMultiScale(gray,scaleFactor=1.1,minNeighbors=5,minSize=(30,30),flags=cv2.CASCADE_SCALE_IMAGE)

    canvas = np.zeros((250, 300, 3), dtype="uint8")

    imgClone = img.copy()

    # to classifiy image and get the prediction in label 
    my_labels = []
    if len(faces) > 0:
        for face in faces: 
            (fX, fY, fW, fH) = face
            # Extract the ROI of the face from the grayscale image, resize it to a fixed 48x48 pixels, and then prepare
            # the ROI for classification via the CNN
            roi = gray[fY:fY + fH, fX:fX + fW]
            roi = cv2.resize(roi, (48, 48))
            roi = roi.astype("float") / 255.0
            roi = img_to_array(roi)
            roi = np.expand_dims(roi, axis=0)

            preds = emotion_classifier.predict(roi)[0]
            emotion_probability = np.max(preds)
            label_ar = EMOTIONS_ar[preds.argmax()]
            label = EMOTIONS[preds.argmax()]
            my_labels.append(label_ar)

            for (i, (emotion, prob)) in enumerate(zip(EMOTIONS, preds)):
                # construct the label text
                text = "{}: {:.2f}%".format(emotion, prob * 100)
                w = int(prob * 300)
                cv2.rectangle(canvas, (7, (i * 35) + 5),
                            (w, (i * 35) + 35), (0, 0, 255), -1)
                cv2.putText(canvas, text, (10, (i * 35) + 23),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.45,
                        (255, 255, 255), 2)
                cv2.putText(imgClone, label, (fX, fY - 10),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 0, 255), 2)
                cv2.rectangle(imgClone, (fX, fY), (fX + fW, fY + fH),
                                             (0, 0, 255), 2)

    return imgClone, my_labels


def Curr_Pred(img):
    y = ['100', '100', '10', '10', '200', '200', '20', '20', '50', '50', '5', '5']
    # import model
    model = tf.keras.models.load_model("currency/currency_model.h5", compile=False)

    dim = (400, 400)
    img = cv2.resize(img, dim, interpolation = cv2.INTER_AREA)
    img = np.expand_dims(img, axis=0)
    img = img / 255
    pred_probab = model.predict(img)[0]

    ret = ""
    if max(pred_probab) < 0.45:
        ret = "من فضلك أعد تصوير العملة"
    else:
        text = y[list(pred_probab).index(max(pred_probab))]
        if (int(text) < 20):
            ret = text + " جُنَيهات "
        else:
            ret = text + " جُنَيهًا "

    return ret


# This function is used specially with caption output to filter repeated words as it sometimes
# fills in the rest of the output pad limit with the last two or three words from prediction.
def filter_string(result):
    for i in range(1, 6):
        result = result.split(' ')
        print(result)
        lst = []
        temp = ""
        cnt = 0
        for word in result:
            temp += word+' '
            cnt += 1
            if cnt == i:
                lst.append(temp)
                temp = ""
                cnt = 0
        if len(temp):
            lst.append(temp)
        result = ""
        for j in range(len(lst)):
            if j and lst[j]==lst[j-1]:
                continue
            result += lst[j]
    
    return result.strip()
