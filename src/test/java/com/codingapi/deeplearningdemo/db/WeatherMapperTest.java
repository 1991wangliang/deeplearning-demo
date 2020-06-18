package com.codingapi.deeplearningdemo.db;

import com.codingapi.deeplearningdemo.utils.CsvUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.FileStatsStorage;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;
import java.util.Random;

@Slf4j
@SpringBootTest
class WeatherMapperTest {

    //Random number generator seed, for reproducability
    public static final int seed = 12345;
    //Number of epochs (full passes of the data)
    public static final int nEpochs = 10;

    //Batch size: i.e., each epoch has nSamples/batchSize parameter updates
    public static final int batchSize = 100;

    //Network learning rate
    public static final double learningRate = 0.03;


    public static final Random rng = new Random(seed);

    @Autowired
    private WeatherMapper weatherMapper;

    @SneakyThrows
    @Test
    void training() {

        File locationToSave = new File("weather.zip");       //Where to save the network. Note: the file is in .zip format - can be opened externally
        boolean saveUpdater = true;

        MultiLayerNetwork net = null;
        if(locationToSave.exists()) {
            //Load the model
            net = MultiLayerNetwork.load(locationToSave, saveUpdater);
        }else{
            net = build();
            networkTraining(net);
            net.save(locationToSave, saveUpdater);
        }

//        double v1 = 2019;
        double v2 = 4;
        double v3 = 18;

        INDArray input = Nd4j.create(new double[] {v2,v3}, 1, 2);
        input =  input.div(10);
        INDArray out = net.output(input, false);
        out = out.mul(10);
        System.out.println("predict:"+out);

    }


    private  void networkTraining(MultiLayerNetwork net){

        List<Weather> list = weatherMapper.findAll();
        log.info("list->{}",list);
        //Generate the training data
        DataSetIterator iterator =  CsvUtils.getTrainingData(list,batchSize,rng);

        UIServer uiServer = UIServer.getInstance();
        StatsStorage statsStorage = new FileStatsStorage(new File(System.getProperty("java.io.tmpdir"), "ui-stats.dl4j"));
        uiServer.attach(statsStorage);
        net.setListeners(new StatsListener(statsStorage), new ScoreIterationListener(1));

        //Train the network on the full data set, and evaluate in periodically
        for( int i=0; i<nEpochs; i++ ){
            iterator.reset();
            net.fit(iterator);
        }
    }

    private MultiLayerNetwork build(){
        //Create the network
        int numInput = 2;
        int numOutputs = 2;

        MultiLayerNetwork net = new MultiLayerNetwork(new NeuralNetConfiguration.Builder()
                .seed(seed)
                .weightInit(WeightInit.XAVIER)
                .updater(new Nesterovs(learningRate, 0.9))
                .list()
                .layer(new DenseLayer.Builder().nIn(numInput).nOut(500)
                        .activation(Activation.RELU)
                        .build())
                .layer( new DenseLayer.Builder().nIn(500).nOut(300)
                        .activation(Activation.RELU)
                        .build())
                .layer( new DenseLayer.Builder().nIn(300).nOut(100)
                        .activation(Activation.RELU)
                        .build())
                .layer( new DenseLayer.Builder().nIn(100).nOut(50)
                        .activation(Activation.RELU)
                        .build())
                .layer( new DenseLayer.Builder().nIn(50).nOut(30)
                        .activation(Activation.RELU)
                        .build())
                .layer( new DenseLayer.Builder().nIn(30).nOut(5)
                        .activation(Activation.RELU)
                        .build())
                .layer( new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                            .activation(Activation.IDENTITY)
                        .nIn(5).nOut(numOutputs).build())
                .build()
        );
        net.init();

        return net;
    }
}
