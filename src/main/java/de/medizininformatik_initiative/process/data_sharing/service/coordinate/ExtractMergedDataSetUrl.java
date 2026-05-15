package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Variables;

public class ExtractMergedDataSetUrl implements ServiceTask
{
	public ExtractMergedDataSetUrl()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();

		String dataSetUrl = extractDataSetUrl(latestTask);
		Task.TaskOutputComponent dataSetUrlOutput = createDataSetUrlOutput(api, dataSetUrl);

		startTask.addOutput(dataSetUrlOutput);
		variables.updateTask(startTask);
	}

	private String extractDataSetUrl(Task latestTask)
	{
		return latestTask.getInput().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
								&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL.equals(c.getCode())))
				.map(Task.ParameterComponent::getValue).filter(t -> t instanceof UrlType).map(t -> (UrlType) t)
				.map(PrimitiveType::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Task.input:data-set-url missing"));
	}

	private Task.TaskOutputComponent createDataSetUrlOutput(ProcessPluginApi api, String dataSetUrl)
	{
		Task.TaskOutputComponent dataSetUrlOutput = new Task.TaskOutputComponent();
		dataSetUrlOutput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setVersion(api.getProcessPluginDefinition().getResourceVersion())
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);
		dataSetUrlOutput.setValue(new UrlType().setValue(dataSetUrl));

		return dataSetUrlOutput;
	}
}
