package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.EndpointProvider;
import dev.dsf.bpe.v2.service.TaskHelper;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

public class SelectDicTargets implements ServiceTask
{
	public SelectDicTargets()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Stream<String> dic = getDicIdentifiers(api.getTaskHelper(), variables);
		List<Target> targetList = getDicTargets(api.getEndpointProvider(), dic, variables);

		Targets targets = variables.createTargets(targetList);
		variables.setTargets(targets);
	}

	private Stream<String> getDicIdentifiers(TaskHelper helper, Variables variables)
	{
		Task task = variables.getStartTask();
		return helper
				.getInputParameterValues(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier).map(Identifier::getValue);
	}

	private List<Target> getDicTargets(EndpointProvider endpointProvider, Stream<String> identifiers,
			Variables variables)
	{
		return identifiers.map(i -> createTarget(endpointProvider, i, variables)).filter(Optional::isPresent)
				.map(Optional::get).collect(Collectors.toList());
	}

	private Optional<Target> createTarget(EndpointProvider endpointProvider, String identifier, Variables variables)
	{
		return endpointProvider.getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(identifier), CodeSystems.OrganizationRole.dic())
				.map(e -> variables.createTarget(identifier, e.getIdentifierFirstRep().getValue(), e.getAddress(),
						UUID.randomUUID().toString()));
	}
}
