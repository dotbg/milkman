package milkmancli.cmds;

import static milkmancli.utils.StringUtil.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.RequiredArgsConstructor;
import milkman.ctrl.RequestTypeManager;
import milkman.domain.Environment;
import milkman.domain.RequestContainer;
import milkman.domain.ResponseAspect;
import milkman.domain.ResponseContainer;
import milkman.domain.Workspace;
import milkman.persistence.PersistenceManager;
import milkman.ui.commands.EnvironmentTemplater;
import milkman.ui.plugin.RequestTypePlugin;
import milkman.ui.plugin.Templater;
import milkman.ui.plugin.UiPluginManager;
import milkmancli.AspectCliPresenter;
import milkmancli.CliContext;
import milkmancli.TerminalCommand;
import milkmancli.utils.StringUtil;

/**
 * Command that clears the screen.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_={@Inject})
public class ExecRequest extends TerminalCommand {

	private final RequestTypeManager requestTypeManager;
	private final UiPluginManager plugins;
	private final CliContext context;

	public Void call() throws IOException {
		var reqName = getParameterValue("request");
		if (reqName == null)
			return null;
		
		RequestContainer req = lookupRequest(reqName);
		
		ResponseContainer response = executeRequest(req);
		
		presentResponse(response, false);
		
		return null;
	}


	protected RequestContainer lookupRequest(String reqName) {
		RequestContainer req = context.findRequestByIdish(reqName)
						.orElseThrow(()  -> new IllegalArgumentException("Request not found " + reqName));
		return req;
	}


	protected void presentResponse(ResponseContainer response, boolean verbose) {
		StringBuilder b = new StringBuilder();
		boolean isFirst = true;
		List<AspectCliPresenter> aspectPresenters = plugins.loadOrderedSpiInstances(AspectCliPresenter.class);
		for (AspectCliPresenter presenter : aspectPresenters) {
			for(ResponseAspect aspect : response.getAspects()) {
				if (presenter.canHandleAspect(aspect) && (verbose || presenter.isPrimary())) {
					if (!isFirst) {
						b.append(System.lineSeparator());
					}
					if (verbose) {
						b.append(aspect.getName()).append(":").append(System.lineSeparator());
					}
					b.append(presenter.getStringRepresentation(aspect));
					isFirst = false;
				}
			}
		}
		System.out.println(b.toString());
	}


	private ResponseContainer executeRequest(RequestContainer req) {
		RequestTypePlugin requestTypePlugin = requestTypeManager.getPluginFor(req);
		return requestTypePlugin.executeRequest(req, buildTemplater());
	}
	private EnvironmentTemplater buildTemplater() {
		Optional<Environment> activeEnv = context.getCurrentWorkspace().getEnvironments().stream().filter(e -> e.isActive()).findAny();
		List<Environment> globalEnvs = context.getCurrentWorkspace().getEnvironments().stream().filter(e -> e.isGlobal()).collect(Collectors.toList());
		EnvironmentTemplater templater = new EnvironmentTemplater(activeEnv, globalEnvs);
		return templater;
	}


	@Override
	public String getName() {
		return "execute-request";
	}

	@Override
	public String getAlias() {
		return "req";
	}

	@Override
	public String getDescription() {
		return "Executes a given request";
	}


	@Override
	public List<Parameter> createParameters() {
		return List.of(new Parameter("request", "the name of the request to execute", new Completion() {
			@Override
			public Collection<String> getCompletionCandidates() {
				return getAvailableRequestNames().stream()
						.map(StringUtil::stringToId)
						.collect(Collectors.toList());
			}
		}));
	}

	protected List<String> getAvailableRequestNames() {
		if (context.getCurrentCollection() == null)
			return Collections.emptyList();
		
		return context.getCurrentCollection().getRequests().stream()
				.map(r -> r.getName())
				.collect(Collectors.toList());
	}

}