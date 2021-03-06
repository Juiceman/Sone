/*
 * Sone - WebOfTrustUpdater.java - Copyright © 2012 David Roden
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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.pterodactylus.sone.freenet.plugin.PluginException;
import net.pterodactylus.sone.freenet.wot.DefaultIdentity;
import net.pterodactylus.sone.freenet.wot.Identity;
import net.pterodactylus.sone.freenet.wot.OwnIdentity;
import net.pterodactylus.sone.freenet.wot.Trust;
import net.pterodactylus.sone.freenet.wot.WebOfTrustConnector;
import net.pterodactylus.sone.freenet.wot.WebOfTrustException;
import net.pterodactylus.util.logging.Logging;
import net.pterodactylus.util.service.AbstractService;
import net.pterodactylus.util.validation.Validation;

/**
 * Updates WebOfTrust identity data in a background thread because communicating
 * with the WebOfTrust plugin can potentially last quite long.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class WebOfTrustUpdater extends AbstractService {

	/** The logger. */
	private static final Logger logger = Logging.getLogger(WebOfTrustUpdater.class);

	/** Stop job. */
	@SuppressWarnings("synthetic-access")
	private final WebOfTrustUpdateJob stopJob = new WebOfTrustUpdateJob();

	/** The web of trust connector. */
	private final WebOfTrustConnector webOfTrustConnector;

	/** The queue for jobs. */
	private final BlockingQueue<WebOfTrustUpdateJob> updateJobs = new LinkedBlockingQueue<WebOfTrustUpdateJob>();

	/**
	 * Creates a new trust updater.
	 *
	 * @param webOfTrustConnector
	 *            The web of trust connector
	 */
	public WebOfTrustUpdater(WebOfTrustConnector webOfTrustConnector) {
		super("Trust Updater");
		this.webOfTrustConnector = webOfTrustConnector;
	}

	//
	// ACTIONS
	//

	/**
	 * Updates the trust relation between the truster and the trustee. This
	 * method will return immediately and perform a trust update in the
	 * background.
	 *
	 * @param truster
	 *            The identity giving the trust
	 * @param trustee
	 *            The identity receiving the trust
	 * @param score
	 *            The new level of trust (from -100 to 100, may be {@code null}
	 *            to remove the trust completely)
	 * @param comment
	 *            The comment of the trust relation
	 */
	public void setTrust(OwnIdentity truster, Identity trustee, Integer score, String comment) {
		SetTrustJob setTrustJob = new SetTrustJob(truster, trustee, score, comment);
		if (updateJobs.contains(setTrustJob)) {
			updateJobs.remove(setTrustJob);
		}
		logger.log(Level.FINER, "Adding Trust Update Job: " + setTrustJob);
		try {
			updateJobs.put(setTrustJob);
		} catch (InterruptedException e) {
			/* the queue is unbounded so it should never block. */
		}
	}

	/**
	 * Adds the given context to the given own identity.
	 *
	 * @param ownIdentity
	 *            The own identity to add the context to
	 * @param context
	 *            The context to add
	 */
	public void addContext(OwnIdentity ownIdentity, String context) {
		addContextWait(ownIdentity, context, false);
	}

	/**
	 * Adds the given context to the given own identity, waiting for completion
	 * of the operation.
	 *
	 * @param ownIdentity
	 *            The own identity to add the context to
	 * @param context
	 *            The context to add
	 * @return {@code true} if the context was added successfully, {@code false}
	 *         otherwise
	 */
	public boolean addContextWait(OwnIdentity ownIdentity, String context) {
		return addContextWait(ownIdentity, context, true);
	}

	/**
	 * Adds the given context to the given own identity, waiting for completion
	 * of the operation.
	 *
	 * @param ownIdentity
	 *            The own identity to add the context to
	 * @param context
	 *            The context to add
	 * @param wait
	 *            {@code true} to wait for the end of the operation,
	 *            {@code false} to return immediately
	 * @return {@code true} if the context was added successfully, {@code false}
	 *         if the context was not added successfully, or if the job should
	 *         not wait for completion
	 */
	private boolean addContextWait(OwnIdentity ownIdentity, String context, boolean wait) {
		AddContextJob addContextJob = new AddContextJob(ownIdentity, context);
		if (!updateJobs.contains(addContextJob)) {
			logger.log(Level.FINER, "Adding Context Job: " + addContextJob);
			try {
				updateJobs.put(addContextJob);
			} catch (InterruptedException ie1) {
				/* the queue is unbounded so it should never block. */
			}
			if (wait) {
				return addContextJob.waitForCompletion();
			}
		} else if (wait) {
			for (WebOfTrustUpdateJob updateJob : updateJobs) {
				if (updateJob.equals(addContextJob)) {
					return updateJob.waitForCompletion();
				}
			}
		}
		return false;
	}

	/**
	 * Removes the given context from the given own identity.
	 *
	 * @param ownIdentity
	 *            The own identity to remove the context from
	 * @param context
	 *            The context to remove
	 */
	public void removeContext(OwnIdentity ownIdentity, String context) {
		RemoveContextJob removeContextJob = new RemoveContextJob(ownIdentity, context);
		if (!updateJobs.contains(removeContextJob)) {
			logger.log(Level.FINER, "Adding Context Job: " + removeContextJob);
			try {
				updateJobs.put(removeContextJob);
			} catch (InterruptedException ie1) {
				/* the queue is unbounded so it should never block. */
			}
		}
	}

	/**
	 * Sets a property on the given own identity.
	 *
	 * @param ownIdentity
	 *            The own identity to set the property on
	 * @param propertyName
	 *            The name of the property to set
	 * @param propertyValue
	 *            The value of the property to set
	 */
	public void setProperty(OwnIdentity ownIdentity, String propertyName, String propertyValue) {
		SetPropertyJob setPropertyJob = new SetPropertyJob(ownIdentity, propertyName, propertyValue);
		if (updateJobs.contains(setPropertyJob)) {
			updateJobs.remove(setPropertyJob);
		}
		logger.log(Level.FINER, "Adding Property Job: " + setPropertyJob);
		try {
			updateJobs.put(setPropertyJob);
		} catch (InterruptedException e) {
			/* the queue is unbounded so it should never block. */
		}
	}

	/**
	 * Removes a property from the given own identity.
	 *
	 * @param ownIdentity
	 *            The own identity to remove the property from
	 * @param propertyName
	 *            The name of the property to remove
	 */
	public void removeProperty(OwnIdentity ownIdentity, String propertyName) {
		setProperty(ownIdentity, propertyName, null);
	}

	//
	// SERVICE METHODS
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void serviceRun() {
		while (!shouldStop()) {
			try {
				WebOfTrustUpdateJob updateJob = updateJobs.take();
				if (shouldStop() || (updateJob == stopJob)) {
					break;
				}
				logger.log(Level.FINE, "Running Trust Update Job: " + updateJob);
				long startTime = System.currentTimeMillis();
				updateJob.run();
				long endTime = System.currentTimeMillis();
				logger.log(Level.FINE, "Trust Update Job finished, took " + (endTime - startTime) + " ms.");
			} catch (InterruptedException ie1) {
				/* happens, ignore, loop. */
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void serviceStop() {
		try {
			updateJobs.put(stopJob);
		} catch (InterruptedException ie1) {
			/* the queue is unbounded so it should never block. */
		}
	}

	/**
	 * Base class for WebOfTrust update jobs.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class WebOfTrustUpdateJob {

		/** Object for synchronization. */
		@SuppressWarnings("hiding")
		private final Object syncObject = new Object();

		/** Whether the job has finished. */
		private boolean finished;

		/** Whether the job was successful. */
		private boolean success;

		//
		// ACTIONS
		//

		/**
		 * Performs the actual update operation.
		 * <p>
		 * The implementation of this class does nothing.
		 */
		public void run() {
			/* does nothing. */
		}

		/**
		 * Waits for completion of this job or stopping of the WebOfTrust
		 * updater.
		 *
		 * @return {@code true} if this job finished successfully, {@code false}
		 *         otherwise
		 *
		 * @see WebOfTrustUpdater#stop()
		 */
		@SuppressWarnings("synthetic-access")
		public boolean waitForCompletion() {
			synchronized (syncObject) {
				while (!finished && !shouldStop()) {
					try {
						syncObject.wait();
					} catch (InterruptedException ie1) {
						/* we’re looping, ignore. */
					}
				}
				return success;
			}
		}

		//
		// PROTECTED METHODS
		//

		/**
		 * Signals that this job has finished.
		 *
		 * @param success
		 *            {@code true} if this job finished successfully,
		 *            {@code false} otherwise
		 */
		protected void finish(boolean success) {
			synchronized (syncObject) {
				finished = true;
				this.success = success;
				syncObject.notifyAll();
			}
		}

	}

	/**
	 * Base class for WebOfTrust trust update jobs.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class WebOfTrustTrustUpdateJob extends WebOfTrustUpdateJob {

		/** The identity giving the trust. */
		protected final OwnIdentity truster;

		/** The identity receiving the trust. */
		protected final Identity trustee;

		/**
		 * Creates a new trust update job.
		 *
		 * @param truster
		 *            The identity giving the trust
		 * @param trustee
		 *            The identity receiving the trust
		 */
		@SuppressWarnings("synthetic-access")
		public WebOfTrustTrustUpdateJob(OwnIdentity truster, Identity trustee) {
			this.truster = truster;
			this.trustee = trustee;
		}

		//
		// OBJECT METHODS
		//

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object object) {
			if ((object == null) || !object.getClass().equals(getClass())) {
				return false;
			}
			WebOfTrustTrustUpdateJob updateJob = (WebOfTrustTrustUpdateJob) object;
			return ((truster == null) ? (updateJob.truster == null) : updateJob.truster.equals(truster)) && ((trustee == null) ? (updateJob.trustee == null) : updateJob.trustee.equals(trustee));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return getClass().hashCode() ^ ((truster == null) ? 0 : truster.hashCode()) ^ ((trustee == null) ? 0 : trustee.hashCode());
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return String.format("%s[truster=%s,trustee=%s]", getClass().getSimpleName(), (truster == null) ? null : truster.getId(), (trustee == null) ? null : trustee.getId());
		}

	}

	/**
	 * Update job that sets the trust relation between two identities.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class SetTrustJob extends WebOfTrustTrustUpdateJob {

		/** The score of the relation. */
		private final Integer score;

		/** The comment of the relation. */
		private final String comment;

		/**
		 * Creates a new set trust job.
		 *
		 * @param truster
		 *            The identity giving the trust
		 * @param trustee
		 *            The identity receiving the trust
		 * @param score
		 *            The score of the trust (from -100 to 100, may be
		 *            {@code null} to remote the trust relation completely)
		 * @param comment
		 *            The comment of the trust relation
		 */
		public SetTrustJob(OwnIdentity truster, Identity trustee, Integer score, String comment) {
			super(truster, trustee);
			this.score = score;
			this.comment = comment;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("synthetic-access")
		public void run() {
			try {
				if (score != null) {
					if (trustee instanceof DefaultIdentity) {
						((DefaultIdentity) trustee).setTrust(truster, new Trust(score, null, 0));
					}
					webOfTrustConnector.setTrust(truster, trustee, score, comment);
				} else {
					if (trustee instanceof DefaultIdentity) {
						((DefaultIdentity) trustee).setTrust(truster, null);
					}
					webOfTrustConnector.removeTrust(truster, trustee);
				}
				finish(true);
			} catch (WebOfTrustException wote1) {
				logger.log(Level.WARNING, "Could not set Trust value for " + truster + " -> " + trustee + " to " + score + " (" + comment + ")!", wote1);
				finish(false);
			}
		}

	}

	/**
	 * Base class for context updates of an {@link OwnIdentity}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class WebOfTrustContextUpdateJob extends WebOfTrustUpdateJob {

		/** The own identity whose contexts to manage. */
		protected final OwnIdentity ownIdentity;

		/** The context to update. */
		protected final String context;

		/**
		 * Creates a new context update job.
		 *
		 * @param ownIdentity
		 *            The own identity to update
		 * @param context
		 *            The context to update
		 */
		@SuppressWarnings("synthetic-access")
		public WebOfTrustContextUpdateJob(OwnIdentity ownIdentity, String context) {
			Validation.begin().isNotNull("OwnIdentity", ownIdentity).isNotNull("Context", context).check();
			this.ownIdentity = ownIdentity;
			this.context = context;
		}

		//
		// OBJECT METHODS
		//

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object object) {
			if ((object == null) || !object.getClass().equals(getClass())) {
				return false;
			}
			WebOfTrustContextUpdateJob updateJob = (WebOfTrustContextUpdateJob) object;
			return updateJob.ownIdentity.equals(ownIdentity) && updateJob.context.equals(context);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return getClass().hashCode() ^ ownIdentity.hashCode() ^ context.hashCode();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return String.format("%s[ownIdentity=%s,context=%s]", getClass().getSimpleName(), ownIdentity, context);
		}

	}

	/**
	 * Job that adds a context to an {@link OwnIdentity}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class AddContextJob extends WebOfTrustContextUpdateJob {

		/**
		 * Creates a new add-context job.
		 *
		 * @param ownIdentity
		 *            The own identity whose contexts to manage
		 * @param context
		 *            The context to add
		 */
		public AddContextJob(OwnIdentity ownIdentity, String context) {
			super(ownIdentity, context);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("synthetic-access")
		public void run() {
			try {
				webOfTrustConnector.addContext(ownIdentity, context);
				ownIdentity.addContext(context);
				finish(true);
			} catch (PluginException pe1) {
				logger.log(Level.WARNING, String.format("Could not add Context “%2$s” to Own Identity %1$s!", ownIdentity, context), pe1);
				finish(false);
			}
		}

	}

	/**
	 * Job that removes a context from an {@link OwnIdentity}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class RemoveContextJob extends WebOfTrustContextUpdateJob {

		/**
		 * Creates a new remove-context job.
		 *
		 * @param ownIdentity
		 *            The own identity whose contexts to manage
		 * @param context
		 *            The context to remove
		 */
		public RemoveContextJob(OwnIdentity ownIdentity, String context) {
			super(ownIdentity, context);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("synthetic-access")
		public void run() {
			try {
				webOfTrustConnector.removeContext(ownIdentity, context);
				ownIdentity.removeContext(context);
				finish(true);
			} catch (PluginException pe1) {
				logger.log(Level.WARNING, String.format("Could not remove Context “%2$s” to Own Identity %1$s!", ownIdentity, context), pe1);
				finish(false);
			}
		}

	}

	/**
	 * Base class for update jobs that deal with properties.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class WebOfTrustPropertyUpdateJob extends WebOfTrustUpdateJob {

		/** The own identity to update properties on. */
		protected final OwnIdentity ownIdentity;

		/** The name of the property to update. */
		protected final String propertyName;

		/**
		 * Creates a new property update job.
		 *
		 * @param ownIdentity
		 *            The own identity to update the property on
		 * @param propertyName
		 *            The name of the property to update
		 */
		@SuppressWarnings("synthetic-access")
		public WebOfTrustPropertyUpdateJob(OwnIdentity ownIdentity, String propertyName) {
			this.ownIdentity = ownIdentity;
			this.propertyName = propertyName;
		}

		//
		// OBJECT METHODS
		//

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object object) {
			if ((object == null) || !object.getClass().equals(getClass())) {
				return false;
			}
			WebOfTrustPropertyUpdateJob updateJob = (WebOfTrustPropertyUpdateJob) object;
			return updateJob.ownIdentity.equals(ownIdentity) && updateJob.propertyName.equals(propertyName);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return getClass().hashCode() ^ ownIdentity.hashCode() ^ propertyName.hashCode();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return String.format("%s[ownIdentity=%s,propertyName=%s]", getClass().getSimpleName(), ownIdentity, propertyName);
		}

	}

	/**
	 * WebOfTrust update job that sets a property on an {@link OwnIdentity}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class SetPropertyJob extends WebOfTrustPropertyUpdateJob {

		/** The value of the property to set. */
		private final String propertyValue;

		/**
		 * Creates a new set-property job.
		 *
		 * @param ownIdentity
		 *            The own identity to set the property on
		 * @param propertyName
		 *            The name of the property to set
		 * @param propertyValue
		 *            The value of the property to set
		 */
		public SetPropertyJob(OwnIdentity ownIdentity, String propertyName, String propertyValue) {
			super(ownIdentity, propertyName);
			this.propertyValue = propertyValue;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("synthetic-access")
		public void run() {
			try {
				if (propertyValue == null) {
					webOfTrustConnector.removeProperty(ownIdentity, propertyName);
					ownIdentity.removeProperty(propertyName);
				} else {
					webOfTrustConnector.setProperty(ownIdentity, propertyName, propertyValue);
					ownIdentity.setProperty(propertyName, propertyValue);
				}
				finish(true);
			} catch (PluginException pe1) {
				logger.log(Level.WARNING, String.format("Could not set Property “%2$s” to “%3$s” on Own Identity %1$s!", ownIdentity, propertyName, propertyValue), pe1);
				finish(false);
			}
		}

	}

}
