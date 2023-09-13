package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class ExtractMergedDataSetUrl extends AbstractServiceDelegate
{
	public ExtractMergedDataSetUrl(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task currentTask = variables.getLatestTask();
		Task leadingTask = variables.getStartTask();

		String dataSetUrl = extractDataSetUrl(currentTask);
		Task.TaskOutputComponent dataSetUrlOutput = createDataSetUrlOutput(dataSetUrl);

		leadingTask.addOutput(dataSetUrlOutput);
		variables.updateTask(leadingTask);
	}

	private String extractDataSetUrl(Task currentTask)
	{
		return currentTask.getInput().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
								&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL.equals(c.getCode())))
				.map(Task.ParameterComponent::getValue).filter(t -> t instanceof UrlType).map(t -> (UrlType) t)
				.map(PrimitiveType::getValue).findFirst().orElseThrow(() -> new RuntimeException(
						"Could not find data-set URL in Task with id '" + currentTask.getId() + "'"));
	}

	private Task.TaskOutputComponent createDataSetUrlOutput(String dataSetUrl)
	{
		Task.TaskOutputComponent dataSetUrlOutput = new Task.TaskOutputComponent();
		dataSetUrlOutput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);
		dataSetUrlOutput.setValue(new UrlType().setValue(dataSetUrl));

		return dataSetUrlOutput;
	}
}
