package de.medizininformatik_initiative.process.data_sharing.spring.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.medizininformatik_initiative.process.data_sharing.variables.ResearchersSerializer;

@Configuration
public class DataSharingVariablesConfig
{
	@Autowired
	private ObjectMapper objectMapper;

	@Bean
	public ResearchersSerializer researchersSerializer()
	{
		return new ResearchersSerializer(objectMapper);
	}
}
