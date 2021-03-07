import tensorflow as tf
import numpy as np
import pickle


with open('caption/word_index_60k.pkl', "rb") as f:
    word_index = pickle.load(f)

with open('caption/index_word_60k.pkl', "rb") as f:
    index_word = pickle.load(f)

with open('caption/embedding_matrix_60k.pkl', "rb") as f:
    embedding_matrix = pickle.load(f)

vocab_size = len(word_index)+1
embedding_dim = 512
units = 512
max_length = 15
features_shape = 2048
attention_features_shape = 64
num_boxes = 64

def load_image(img):
    # img = tf.io.read_file(image_path)
    # img = tf.image.decode_jpeg(tf.convert_to_tensor(img), channels=3)
    img = tf.image.resize(img, (299, 299))
    img = tf.keras.applications.inception_v3.preprocess_input(img)
    return img


class BahdanauAttention(tf.keras.Model):
  def __init__(self, units):
    super(BahdanauAttention, self).__init__()
    self.W1 = tf.keras.layers.Dense(units)
    self.W2 = tf.keras.layers.Dense(units)
    self.V = tf.keras.layers.Dense(1)

  def call(self, features, hidden):
    # features(CNN_encoder output) shape == (batch_size, 64, embedding_dim)

    # hidden shape == (batch_size, hidden_size)
    # hidden_with_time_axis shape == (batch_size, 1, hidden_size)
    hidden_with_time_axis = tf.expand_dims(hidden, 1)

    # score shape == (batch_size, 64, hidden_size)
    score = tf.nn.tanh(self.W1(features) + self.W2(hidden_with_time_axis)) # equation a = wa.T*tanh(wva*vi+wha*h1(t))

    # attention_weights shape == (batch_size, 64, 1)
    # you get 1 at the last axis because you are applying score to self.V
    attention_weights = tf.nn.softmax(self.V(score), axis=1) #equation alpha = softmax(a) give us normalized attention weights

    # context_vector shape after sum == (batch_size, hidden_size)
    context_vector = attention_weights * features 
    # the attended image feature used as input to the language model 
    context_vector = tf.reduce_sum(context_vector, axis=1) # v^(t)

    return context_vector, attention_weights

def Model():
    Features = tf.keras.layers.Input(shape=(64,2048),name = 'features') 
    Targets = tf.keras.layers.Input(shape=(1,),name = 'target')
    num_of_boxes = 64
    pooled_features = tf.keras.backend.sum(Features, axis = 1)/num_of_boxes #get a shape of (batch_size,2048)
    embedding = tf.keras.layers.Embedding(vocab_size, embedding_dim,weights=[embedding_matrix],trainable=False)
    
    x = embedding(Targets) #get a shape of (batch_size,15,300)
    
    x = tf.concat([tf.expand_dims(pooled_features, 1), x], axis=-1)
    lstm_attention = tf.keras.layers.LSTM(units, return_sequences=True, return_state=True, recurrent_initializer='glorot_uniform') 
    
    output_attention_lstm, state_attention_lstm, *_ = lstm_attention(x) # h1(t)
    
    attention_layers = BahdanauAttention(units)
    
    final_context_vector, final_attention_weights, *_ = attention_layers(Features, state_attention_lstm)
    
    final_attention_weights = tf.expand_dims(final_attention_weights, 1)

    x = tf.concat([tf.expand_dims(final_context_vector, 1), tf.expand_dims(state_attention_lstm,1)], axis = -1)

    lstm_output = tf.keras.layers.LSTM(units,
                                 return_sequences = True,
                                 return_state = True,
                                 recurrent_initializer='glorot_uniform')

    output_lstm, state_output_lstm, *_ = lstm_output(x)

    fc1 = tf.keras.layers.Dense(units)
    fc = tf.keras.layers.Dense(vocab_size)

    x = fc1(output_lstm)
    x = tf.reshape(x, (-1, x.shape[2]))

    out = fc(x)
    # state_output_lstm = tf.keras.layers.Input(shape=(512,))
    model = tf.keras.Model(inputs=[Features, Targets], outputs=[out])
    loss_object = tf.keras.losses.CategoricalCrossentropy(from_logits=True, reduction='none')
    model.compile(optimizer=tf.keras.optimizers.Adam(), loss=loss_object)
    return model


def pad_up_to(t, max_in_dims, constant_values):
    s = tf.shape(t)
    paddings = [[0, m-s[i]] for (i,m) in enumerate(max_in_dims)]
    return tf.pad(t, paddings, 'CONSTANT', constant_values=constant_values)




def evaluate(image):
    # Download Inceptionv3 model and get the last layer 
    image_model = tf.keras.applications.InceptionV3(include_top=False, weights='imagenet')
    new_input = image_model.input
    hidden_layer = image_model.layers[-1].output
    image_features_extract_model = tf.keras.Model(new_input, hidden_layer)

    model = Model()
    model.load_weights('caption/caption_weights.h5')
    
    # hidden = decoder.reset_state(batch_size=1)
    img = load_image(image)
    img = tf.expand_dims(img, axis=0)
    img_tensor_val = image_features_extract_model(img)
    img_tensor_val = tf.reshape(img_tensor_val,
                              (img_tensor_val.shape[0]*img_tensor_val.shape[1]*img_tensor_val.shape[2], img_tensor_val.shape[3]))
    img_tensor_val = pad_up_to(img_tensor_val, [num_boxes, features_shape], 0)
    img_tensor_val = tf.reshape(img_tensor_val, (-1, img_tensor_val.shape[0], img_tensor_val.shape[1]))

    #img_tensor_val = tf.reshape(img_tensor_val, (img_tensor_val.shape[0], -1, img_tensor_val.shape[3]))

    dec_input = tf.expand_dims([word_index['<start>']], 0)
    result = []

    for i in range(max_length):
        #predictions, hidden, attention_weights = decoder(dec_input, features, hidden)
        predictions = model.predict([img_tensor_val, dec_input], verbose=0, steps = 1)
    
        predicted_id = np.argmax(predictions)
        
        result.append(index_word[predicted_id])

        if index_word[predicted_id] == '<end>':
            return result[:-1]

        dec_input = tf.expand_dims([predicted_id], 0)
    return result[:-1]
