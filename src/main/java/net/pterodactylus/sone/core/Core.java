/*
 * FreenetSone - Core.java - Copyright © 2010 David Roden
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.pterodactylus.sone.core;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.pterodactylus.sone.data.Sone;
import net.pterodactylus.util.config.Configuration;
import net.pterodactylus.util.config.ConfigurationException;
import net.pterodactylus.util.logging.Logging;
import net.pterodactylus.util.service.AbstractService;
import net.pterodactylus.util.text.StringEscaper;
import net.pterodactylus.util.text.TextException;
import freenet.keys.FreenetURI;

/**
 * The Sone core.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class Core extends AbstractService {

	/** The logger. */
	private static final Logger logger = Logging.getLogger(Core.class);

	/** The configuration. */
	private Configuration configuration;

	/** Interface to freenet. */
	private FreenetInterface freenetInterface;

	/** The local Sones. */
	private final Set<Sone> localSones = new HashSet<Sone>();

	/**
	 * Creates a new core.
	 */
	public Core() {
		super("Sone Core");
	}

	//
	// ACCESSORS
	//

	/**
	 * Sets the configuration of the core.
	 *
	 * @param configuration
	 *            The configuration of the core
	 * @return This core (for method chaining)
	 */
	public Core configuration(Configuration configuration) {
		this.configuration = configuration;
		return this;
	}

	/**
	 * Sets the Freenet interface to use.
	 *
	 * @param freenetInterface
	 *            The Freenet interface to use
	 * @return This core (for method chaining)
	 */
	public Core freenetInterface(FreenetInterface freenetInterface) {
		this.freenetInterface = freenetInterface;
		return this;
	}

	/**
	 * Returns the local Sones.
	 *
	 * @return The local Sones
	 */
	public Set<Sone> localSones() {
		return localSones;
	}

	//
	// ACTIONS
	//

	//
	// SERVICE METHODS
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void serviceStart() {
		loadConfiguration();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void serviceStop() {
		saveConfiguration();
	}

	//
	// PRIVATE METHODS
	//

	/**
	 * Loads the configuration.
	 */
	private void loadConfiguration() {
		logger.entering(Core.class.getName(), "loadConfiguration()");

		/* get names of all local Sones. */
		String allSoneNamesString = configuration.getStringValue("Sone/Names").getValue(null);
		if (allSoneNamesString == null) {
			allSoneNamesString = "";
		}
		List<String> allSoneNames;
		try {
			allSoneNames = StringEscaper.parseLine(allSoneNamesString);
		} catch (TextException te1) {
			logger.log(Level.WARNING, "Could not parse Sone names: “" + allSoneNamesString + "”", te1);
			allSoneNames = Collections.emptyList();
		}

		/* parse local Sones. */
		logger.log(Level.INFO, "Loading %d Sones…", allSoneNames.size());
		for (String soneName : allSoneNames) {
			String id = configuration.getStringValue("Sone/Name." + soneName + "/ID").getValue(null);
			String insertUri = configuration.getStringValue("Sone/Name." + soneName + "/InsertURI").getValue(null);
			String requestUri = configuration.getStringValue("Sone/Name." + soneName + "/RequestURI").getValue(null);
			try {
				localSones.add(new Sone(UUID.fromString(id), soneName, new FreenetURI(requestUri), new FreenetURI(insertUri)));
			} catch (MalformedURLException mue1) {
				logger.log(Level.WARNING, "Could not create Sone from requestUri (“" + requestUri + "”) and insertUri (“" + insertUri + "”)!", mue1);
			}
		}

		logger.exiting(Core.class.getName(), "loadConfiguration()");
	}

	/**
	 * Saves the configuraiton.
	 */
	private void saveConfiguration() {

		/* get the names of all Sones. */
		Set<String> soneNames = new HashSet<String>();
		for (Sone sone : localSones) {
			soneNames.add(sone.getName());
		}
		String soneNamesString = StringEscaper.escapeWords(soneNames);

		logger.log(Level.INFO, "Storing %d Sones…", soneNames.size());
		try {
			/* store names of all Sones. */
			configuration.getStringValue("Sone/Names").setValue(soneNamesString);

			/* store all Sones. */
			for (Sone sone : localSones) {
				configuration.getStringValue("Sone/Name." + sone.getName() + "/ID").setValue(sone.getId());
				configuration.getStringValue("Sone/Name." + sone.getName() + "/RequestURI").setValue(sone.getRequestUri().toString());
				configuration.getStringValue("Sone/Name." + sone.getName() + "/InsertURI").setValue(sone.getInsertUri().toString());
			}
		} catch (ConfigurationException ce1) {
			logger.log(Level.WARNING, "Could not store configuration!", ce1);
		}
	}

}
