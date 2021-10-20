package susmit.tools.custom.wiring;

import java.util.HashMap;

import org.mule.runtime.api.artifact.Registry;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.construct.Flow;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.event.EventContextFactory;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.WiringFactory;

//Gets executed when the GraphQL server is executed against a query or mutation
public class DynamicWiringFactory implements WiringFactory {

	private static final Logger logger = LoggerFactory.getLogger(DynamicWiringFactory.class);
	
    private final Registry registry;

    private final String configName;

    public DynamicWiringFactory(Registry registry, String configName) {
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

        return AsyncDataFetcher.async(dfenv -> fetchCallingFlow(dfenv));
    }

	

    private Object fetchCallingFlow(DataFetchingEnvironment environment) {
        String fieldName = environment.getFieldDefinition().getName();

        //logger.debug("Need to retrieve flow for type name: " + fieldName);

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
        }

        return null;
    }
	
    private boolean flowExistsForField(String fieldName) {
        return  lookupFlowForField(fieldName) != null;
    }

    private Flow lookupFlowForField(String fieldName) {
        return (Flow) registry.lookupByName("graphql:" + fieldName).orElse(null);
    }
    
}
