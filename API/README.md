# API


## Overview:

A flask API for all models used in IVA project.

The API has a resource for each service that takes a json input of a base64 encoded image, and a text question in case of vqa:

{

  'image': *base64 encoded image*,
  
  'question': 'some text question'
  
}

And return a text result, and a base64 encoded sound obtained from Google's text-to-speech:

{

  'text': 'some text result',
  
  'sound': *base64 encoded sound result*
  
}

## Usage:

- First, run the download_files.py script to download data required for each model in its corresponding folder.
- Add you GCP project's json credentials file in ocr folder that would be used in both ocr and tts, and add its name in utils.py script. (Optional if you want sound results and OCR service)
- Create a virtual environment for python and download the libraries in requirements.txt with 'pip install -r requirements.txt'
- Run the flask app with command: 'flask run' in the same folder.
- Run each test requests in requests folder to try them.

You can use each individual model in your applications as well.
