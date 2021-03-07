import boto3
import botocore
from botocore import UNSIGNED
from botocore.config import Config

BUCKET_NAME = 'iva-models-eced-2020' 
files = {'caption': ['caption_weights.h5', 'embedding_matrix_60k.pkl', 'index_word_60k.pkl', 'word_index_60k.pkl'],
         'vqa': ['vqa_model.h5', 'topAnsIndexWord2.pkl', 'tokenizer2.json'],
         'currency': ['currency_model.h5'],
         'emotion': ['gpu_mini_XCEPTION.63-0.64.hdf5', 'haarcascade_frontalface_default.xml']}

s3 = boto3.resource('s3', config=Config(signature_version=UNSIGNED))


for key in files.keys():
    for item in files[key]:
        s3.Bucket(BUCKET_NAME).download_file(item, key + '/' + item)
        print(key + '/' + item + ' is downloaded')
