package susmit.tools.custom.wiring;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderContextProvider;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.MappedBatchLoaderWithContext;
import org.mule.runtime.api.artifact.Registry;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.construct.Flow;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.event.EventContextFactory;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.WiringFactory;
import susmit.tools.custom.error.GraphQLCustomError;

//Gets executed when the GraphQL server is executed against a query or mutation
public class DynamicWiringFactoryDL implements WiringFactory {

	private static final Logger logger = LoggerFactory.getLogger(DynamicWiringFactoryDL.class);
	
    private final Registry registry;

    private final String configName;
    
    private BatchLoaderWithContext<String, Object> batchLoader = new BatchLoaderWithContext<String, Object>() {
        @Override
        public CompletableFuture<List<Object>> load(List<String> keys, BatchLoaderEnvironment environment) {
            
        	DataFetchingEnvironment dfenv = (DataFetchingEnvironment)environment.getContext();
        	
        	return CompletableFuture.supplyAsync(() 
        				-> fetchCallingFlowWithKeys(dfenv, keys));
        	
        }
	
    };
    
    
    public DynamicWiringFactoryDL(Registry registry, String configName) {
        this.registry = registry;
        this.configName = configName;
        logger.error("Config name is: " + configName);
    }
    
        
    @Override
    public boolean providesDataFetcher(FieldWiringEnvironment environment) {

        logger.debug("Called provides data fetcher: " + environment.getFieldDefinition().getName());
        
        String fieldName = environment.getFieldDefinition().getName();

        logger.debug("Called provides data fetcher: " + fieldName);

        return flowExistsForField(fieldName);
    }
    

    @Override
    public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {

        logger.debug("Called get data fetcher: " + environment.getFieldDefinition().getName());

        DataFetcher<Object> dataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                
            	String dlName = environment.getFieldDefinition().getName();
            	
            	// root
            	if(environment.getSource() == null) return fetchCallingFlow(environment);
            	
            	//Batch Loader
            	DataLoader<String,Object> dataLoader = environment.getDataLoader(dlName);
                if(dataLoader == null)
                {
                	dataLoader = DataLoaderFactory.newDataLoader(batchLoader,DataLoaderOptions.newOptions().setCachingEnabled(false).setBatchLoaderContextProvider(() -> environment));
                	environment.getDataLoaderRegistry().register(dlName, dataLoader);
                }
                
               
                return dataLoader.load( ((Map)environment.getSource()).get("id").toString() ); // Keys, DF Environment
        }
        };
        	return dataFetcher;      
        
    }
    
	
    //This is called with keys
    private List<Object> fetchCallingFlowWithKeys(DataFetchingEnvironment environment, List<String> keys) {
        
    	String fieldName = environment.getFieldDefinition().getName();

        logger.debug("Calling flow: " + fieldName + "with keys: " + keys.toString());

        Flow flow = (Flow) lookupFlowForField(fieldName);

        logger.debug("Invoking flow: " + flow.getName());

        //Inject the context and keys into header
        HashMap <String, Object> attr = new HashMap<String, Object>();
        attr.put("keys", keys.toArray());
        attr.put("environment", environment);
        
        Message msg = Message.builder()
                .nullValue()
                .attributesValue(attr) //Keys serialized as attribute
                .build();

        CoreEvent evt = CoreEvent.builder(EventContextFactory.create(flow, DefaultComponentLocation.fromSingleComponent(configName))).message(msg).build();

        try {

            evt = flow.process(evt);
            logger.debug("Got payload type: " + evt.getMessage().getPayload().getValue() + " type: " + evt.getMessage().getPayload().getValue().getClass());
            return (List<Object>)evt.getMessage().getPayload().getValue();

        } catch (MuleException ex) {
            logger.error("Error while trying to execute the flow", ex);
            throw new ModuleException(GraphQLCustomError.EXECUTION_FAILURE, ex);
        }
    }
    

    private Object fetchCallingFlow(DataFetchingEnvironment environment) {
        String fieldName = environment.getFieldDefinition().getName();

       // logger.debug("Need to retrieve flow for type name: " + fieldName);

        Flow flow = (Flow) lookupFlowForField(fieldName);

        logger.debug("Invoking flow: " + flow.getName());
        
        //Inject the context and keys into header
        HashMap <String, Object> attr = new HashMap<String, Object>();
        attr.put("environment", environment);

        Message msg = Message.builder()
                .nullValue()
                .attributesValue(attr) //DataFetchingEnvironment serialized as attribute
                .build();

        CoreEvent evt = CoreEvent.builder(EventContextFactory.create(flow, DefaultComponentLocation.fromSingleComponent(configName))).message(msg).build();

        try {

            evt = flow.process(evt);
            return evt.getMessage().getPayload().getValue();

        } catch (MuleException ex) {
            logger.error("Error while trying to execute the flow", ex);
            throw new ModuleException(GraphQLCustomError.EXECUTION_FAILURE, ex);
        }
    }
	
    private boolean flowExistsForField(String fieldName) {
        return  lookupFlowForField(fieldName) != null;
    }

    private Flow lookupFlowForField(String fieldName) {
        return (Flow) registry.lookupByName("graphql:" + fieldName).orElse(null);
    }
    
}
