package susmit.tools.custom;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;

import javax.inject.Inject;

import org.mule.runtime.api.artifact.Registry;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import susmit.tools.custom.wiring.DynamicWiringFactory;
import susmit.tools.custom.wiring.DynamicWiringFactoryDL;

import org.mule.runtime.api.lifecycle.Startable;

/**
 * This class represents an extension configuration, values set in this class
 * are commonly used across multiple operations since they represent something
 * core from the extension.
 */
@Operations(GraphQLOperations.class)
public class GraphQLConfiguration implements Startable {

	private static final Logger logger = LoggerFactory.getLogger(GraphQLConfiguration.class);

	@Inject
	private Registry registry;

	@Parameter
	private String schemaList;
	
	@Parameter
	private String idFieldName;
	
	private GraphQL graphQLServer;
	
	@Override
	public void start() throws MuleException {
		
		try
		{

		logger.debug("Loading GraphQL configurations");
		
		SchemaParser schemaParser = new SchemaParser();
		SchemaGenerator schemaGenerator = new SchemaGenerator();
		TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
		
		//Loads multiple schemas separated by comma
		Arrays.asList(schemaList.split(",")).forEach( schemaName -> {
			typeRegistry.merge(schemaParser.parse(loadSchema(schemaName)));	
		});
		
		logger.debug("Parsed GraphQL schemas");
		
		GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, buildRuntimeWiring());
		setGraphQLServer(graphQLSchema);
		
		logger.debug("GraphQL server loaded");
		
		}
		catch(Exception ex)
		{
			logger.error("Error occured in loading configuration: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private InputStream loadSchema(String schema)
	{
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(schema);
	}
	
	RuntimeWiring buildRuntimeWiring()
	{
		return RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new DynamicWiringFactoryDL(registry, this.getClass().getName())).build();
	}
	
	Registry getRegistry() {
		return registry;
	}

	GraphQL getGraphQLServer() {
		return graphQLServer;
	}

	private void setGraphQLServer(GraphQLSchema graphQLSchema)
	{
		this.graphQLServer = GraphQL.newGraphQL(graphQLSchema).build();
	}

	public String getIdFieldName() {
		return idFieldName;
	}

	
	
}
