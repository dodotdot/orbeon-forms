/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event

import events._
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml._
import java.util.{ArrayList, List ⇒ JList, Set ⇒ JSet, Collections ⇒ JCollections}
import dom4j.{LocationSAXContentHandler, Dom4jUtils}
import org.orbeon.oxf.pipeline.api._
import org.dom4j.{Document, Element}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.util.{IndentedLogger, Multipart, Logging}
import XFormsEvents._
import collection.JavaConverters._
import org.orbeon.oxf.xforms.analysis.controls.RepeatControl

// Process events sent by the client, including sorting, filtering, and security
object ClientEvents extends Logging {

    private val EventParameters = List("dnd-start", "dnd-end", "modifiers", "text", "file", "filename", "content-type", "content-length")
    private val DummyEvent = List(new LocalEvent(Dom4jUtils.createElement("dummy"), false))

    private case class LocalEvent(element: Element, trusted: Boolean) {
        val name = element.attributeValue("name")
        val targetEffectiveId = element.attributeValue("source-control-id")
        val bubbles = element.attributeValue("bubbles") != "false" // default is true
        val cancelable = element.attributeValue("cancelable") != "false" // default is true
        val value = element.getText
    }

    // Entry point called by the server: process a sequence of incoming client events.
    def processEvents(
            doc: XFormsContainingDocument,
            clientEvents: JList[Element],
            serverEvents: JList[Element]): (Boolean, JSet[String], String) = {

        val allClientAndServerEvents = {

            // Process events for noscript mode if needed
            val clientEventsAfterNoscript =
                if (doc.getStaticState.isNoscript)
                    reorderNoscriptEvents(clientEvents.asScala, doc)
                else
                    clientEvents.asScala

            // Decode encrypted server events
            def decodeServerEvents(element: Element) = {
                val document = decodeXML(element.getStringValue)
                Dom4j.elements(document.getRootElement, XXFORMS_EVENT_QNAME)
            }

            // Decode global server events
            val globalServerEvents: Seq[LocalEvent] = serverEvents.asScala flatMap decodeServerEvents map (LocalEvent(_, trusted = true))

            // Gather all events including decoding action server events
            globalServerEvents ++
                (clientEventsAfterNoscript flatMap {
                    case element if element.attributeValue("name") == XXFORMS_SERVER_EVENTS ⇒
                        decodeServerEvents(element) map (LocalEvent(_, trusted = true))
                    case element ⇒
                        List(LocalEvent(element, trusted = false))
                })
        }

        if (allClientAndServerEvents.nonEmpty) {

            def filterEvents(events: Seq[LocalEvent]) = events filter {
                case a if a.name == XXFORMS_ALL_EVENTS_REQUIRED ⇒ false
                case a if (a.name eq null) && (a.targetEffectiveId eq null) ⇒
                    throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.")
                case _ ⇒ true
            }

            def combineValueEvents(events: Seq[LocalEvent]): Seq[XFormsEvent] = events match {
                case Seq() ⇒ Seq()
                case Seq(localEvent) ⇒ safelyCreateAndMapEvent(doc, localEvent).toList
                case _ ⇒

                    // Grouping key for value change events
                    case class EventGroupingKey(name: String, targetId: String) {
                        def this(localEvent: LocalEvent) =
                            this(localEvent.name, localEvent.targetEffectiveId)
                    }

                    // Slide over the events so we can filter and compress them
                    // NOTE: Don't use Iterator.toSeq as that returns a Stream, which evaluates lazily. This would be great, except
                    // that we *must* first create all events, then dispatch them, so that references to XFormsTarget are obtained
                    // beforehand.
                    (events ++ DummyEvent).sliding(2).toList flatMap {
                        case Seq(a, b) ⇒
                            if (a.name != XXFORMS_VALUE || new EventGroupingKey(a) != new EventGroupingKey(b))
                                safelyCreateAndMapEvent(doc, a)
                            else
                                None
                    }
            }

            // Combine and process events
            for (event ← combineValueEvents(filterEvents(allClientAndServerEvents)))
                processEvent(doc, event)

            // Gather some metadata about the events received to help with the response to the client

            // Whether we got a request for all events
            val gotAllEvents = allClientAndServerEvents exists
                (_.name == XXFORMS_ALL_EVENTS_REQUIRED)

            // Set of all control ids for which we got value events
            val valueChangeControlIds = allClientAndServerEvents collect
                { case e if e.name == XXFORMS_VALUE ⇒ e.targetEffectiveId } toSet

            // Last client focus event received
            val clientFocusControlId = allClientAndServerEvents.reverse find
                (_.name == XFORMS_FOCUS) map
                    (_.targetEffectiveId) orNull

            (gotAllEvents, valueChangeControlIds.asJava, clientFocusControlId)

        } else
            (false, JCollections.emptySet[String], null)
    }

    // NOTE: Leave public for unit tests
    def reorderNoscriptEvents(eventElements: Seq[Element], doc: XFormsContainingDocument): Seq[Element] = {

        // Event categories
        sealed trait Category
        case object Other extends Category
        case object ValueChange extends Category
        case object SelectBlank extends Category
        case object Activation extends Category

        // All categories in the order we want them
        val AllCategories = Seq(Other, ValueChange, SelectBlank, Activation)

        // Group events in 3 categories
        def getEventCategory(element: Element) = element match {
            // Special event for noscript mode
            case element if element.attributeValue("name") == XXFORMS_VALUE_OR_ACTIVATE ⇒
                val sourceControlId = element.attributeValue("source-control-id")
                element match {
                    // This is a value event
                    case element if doc.getStaticOps.isValueControl(sourceControlId) ⇒ ValueChange
                    // This is most likely a trigger or submit which will translate into a DOMActivate. We will move it
                    // to the end so that value change events are committed to instance data before that.
                    case _ ⇒ Activation
                }
            case _ ⇒ Other
        }

        // NOTE: map keys are not in predictable order, but map values preserve the order
        val groups = eventElements groupBy getEventCategory

        // Special handling of checkboxes blanking in noscript mode
        val blankEvents = {

            // Get set of all value change events effective ids
            def getValueChangeIds = groups.get(ValueChange).flatten map (_.attributeValue("source-control-id")) toSet

            // Create <xxf:event name="xxforms-value-or-activate" source-control-id="my-effective-id"/>
            def createBlankingEvent(control: XFormsControl) = {
                val newEventElement = Dom4jUtils.createElement(XXFORMS_EVENT_QNAME)
                newEventElement.addAttribute("name", XXFORMS_VALUE_OR_ACTIVATE)
                newEventElement.addAttribute("source-control-id", control.getEffectiveId)
                newEventElement
            }

            val selectFullControls = doc.getControls.getCurrentControlTree.getSelectFullControls

            // Find all relevant and non-readonly select controls for which no value change event arrived. For each such
            // control, create a new event that will blank its value.
            selectFullControls.asScala.keySet -- getValueChangeIds map
                (selectFullControls.get(_).asInstanceOf[XFormsSelectControl]) filter
                    (control ⇒ control.isRelevant && ! control.isReadonly) map
                        createBlankingEvent toSeq
        }

        // Return all events by category in the order we defined the categories
        AllCategories flatMap ((groups + (SelectBlank → blankEvents)).get(_)) flatten
    }

    // Incoming ids can have the form `my-repeat·1` in order to target a repeat iteration. This is ambiguous without
    // knowing that `my-repeat` refers to a repeat and without knowing the repeat hierarchy, so we should change it
    // in the future, but in the meanwhile we map this id to `my-repeat~iteration·1` based on static information.
    // NOTE: Leave public for unit tests
    def adjustIdForRepeatIteration(doc: XFormsContainingDocument, effectiveId: String) =
        doc.getStaticOps.getControlAnalysis(getPrefixedId(effectiveId)) match {
            case repeat: RepeatControl if RepeatControl.getAllAncestorRepeatsAcrossParts(repeat).size == getEffectiveIdSuffixParts(effectiveId).size - 1 ⇒
                getRelatedEffectiveId(effectiveId, repeat.iteration.get.staticId)
            case _ ⇒
                effectiveId
        }

    private def safelyCreateAndMapEvent(doc: XFormsContainingDocument, event: LocalEvent): Option[XFormsEvent] = {

        implicit val CurrentLogger = doc.getIndentedLogger(LOGGING_CATEGORY)

        // Get event target
        val eventTarget = doc.getObjectByEffectiveId(deNamespaceId(doc, adjustIdForRepeatIteration(doc, event.targetEffectiveId))) match {
            case eventTarget: XFormsEventTarget ⇒ eventTarget
            case _ ⇒
                debug("ignoring client event with invalid target id", Seq("target id" → event.targetEffectiveId, "event name" → event.name))
                return None
        }

        // Check whether the external event is allowed on the given target.
        def checkAllowedExternalEvents = {

            // Whether an external event name is explicitly allowed by the configuration.
            def isExplicitlyAllowedExternalEvent = {
                val externalEventsMap = doc.getStaticState.getAllowedExternalEvents
                ! XFormsEventFactory.isBuiltInEvent(event.name) && externalEventsMap.contains(event.name)
            }

            // This is also a security measure that also ensures that somebody is not able to change values in an instance
            // by hacking external events.
            isExplicitlyAllowedExternalEvent || {
                val explicitlyAllowed = eventTarget.allowExternalEvent(event.name)
                if (! explicitlyAllowed)
                    debug("ignoring invalid client event on target", Seq("id" → eventTarget.getEffectiveId, "event name" → event.name))
                explicitlyAllowed
            }
        }

        // Check the event is allowed on target
        if (event.trusted)
            // Event is trusted, don't check if it is allowed
            debug("processing trusted event", Seq("target id" → eventTarget.getEffectiveId, "event name" → event.name))
        else if (! checkAllowedExternalEvents)
            return None // event is not trusted and is not allowed

        def mapEventName(event: LocalEvent, eventTarget: XFormsEventTarget) = event.name match {
            // Rewrite event type. This is special handling of xxforms-value-or-activate for noscript mode.
            // NOTE: We do this here, because we need to know the actual type of the target. Could do this statically if
            // the static state kept type information for each control.
            case XXFORMS_VALUE_OR_ACTIVATE ⇒
                eventTarget match {
                    // Handler produces:
                    //   <button type="submit" name="foobar" value="activate">...
                    //   <input type="submit" name="foobar" value="Hi There">...
                    //   <input type="image" name="foobar" value="Hi There" src="...">...

                    // IE 6/7 are terminally broken: they don't send the value back, but the contents of the label. So
                    // we must test for any empty content here instead of !"activate".equals(valueString). (Note that
                    // this means that empty labels won't work.) Further, with IE 6, all buttons are present when
                    // using <button>, so we use <input> instead, either with type="submit" or type="image". Bleh.
                    case triggerControl: XFormsTriggerControl if event.value.isEmpty ⇒ None
                    // Triggers get a DOM activation
                    case triggerControl: XFormsTriggerControl ⇒ Some(DOM_ACTIVATE)
                    // Other controls get a value change
                    case _ ⇒ Some(XXFORMS_VALUE)
                }
            case eventName ⇒ Some(eventName)
        }

        // Create event
        mapEventName(event, eventTarget) map { eventName ⇒

            def gatherParameters(event: LocalEvent) =
                Map((for {
                        attributeName ← EventParameters
                        attributeValue = event.element.attributeValue(attributeName)
                        if attributeValue ne null
                    } yield (attributeName → attributeValue)): _*)

            XFormsEventFactory.createEvent(doc, eventName, eventTarget, true,
                event.bubbles, event.cancelable, event.value, gatherParameters(event).asJava)
        }
    }

    // Check for and handle events that don't need access to the document but can return an Ajax response rapidly.
    def doQuickReturnEvents(
            xmlReceiver: XMLReceiver,
            request: ExternalContext.Request,
            requestDocument: Document,
            indentedLogger: IndentedLogger,
            logRequestResponse: Boolean,
            clientEvents: JList[Element],
            session: ExternalContext.Session): Boolean = {

        val eventElement = clientEvents.asScala(0)

        // Helper to make it easier to output simple Ajax responses
        def eventResponse(messageType: String, message: String)(block: ContentHandlerHelper ⇒ Unit): Boolean = {
            implicit val CurrentLogger = indentedLogger
            withDebug(message) {
                // Hook-up debug content handler if we must log the response document
                val (responseReceiver, debugContentHandler) =
                    if (logRequestResponse) {
                        val receivers = new ArrayList[XMLReceiver]
                        receivers.add(xmlReceiver)
                        val debugContentHandler = new LocationSAXContentHandler
                        receivers.add(debugContentHandler)

                        (new TeeXMLReceiver(receivers), Some(debugContentHandler))
                    } else
                        (xmlReceiver, None)

                val helper = new ContentHandlerHelper(responseReceiver)
                helper.startDocument()
                helper.startPrefixMapping("xxf", XXFORMS_NAMESPACE_URI)
                helper.startElement("xxf", XXFORMS_NAMESPACE_URI, "event-response")

                block(helper)

                helper.endElement()
                helper.endPrefixMapping("xxf")
                helper.endDocument()

                debugContentHandler foreach
                    (ch ⇒ debugResults(Seq("ajax response" → Dom4jUtils.domToPrettyString(ch.getDocument))))
            }

            true
        }

        eventElement.attributeValue("name") match {
            // Quick response for heartbeat
            case XXFORMS_SESSION_HEARTBEAT ⇒

                if (indentedLogger.isDebugEnabled) {
                    if (session != null)
                        indentedLogger.logDebug("heartbeat", "received heartbeat from client for session: " + session.getId)
                    else
                        indentedLogger.logDebug("heartbeat", "received heartbeat from client (no session available).")
                }

                // Output empty Ajax response
                eventResponse("ajax response", "handling quick heartbeat Ajax response")(helper ⇒ ())

            // Quick response for upload progress
            case XXFORMS_UPLOAD_PROGRESS ⇒

                // Output simple resulting document
                eventResponse("ajax response", "handling quick upload progress Ajax response") { helper ⇒
                    val sourceControlId = eventElement.attributeValue("source-control-id")
                    Multipart.getUploadProgress(request, XFormsStateManager.getRequestUUID(requestDocument), sourceControlId) match {
                        case Some(progress) ⇒

                            helper.startElement("xxf", XXFORMS_NAMESPACE_URI, "action")
                            helper.startElement("xxf", XXFORMS_NAMESPACE_URI, "control-values")
                            helper.element("xxf", XXFORMS_NAMESPACE_URI, "control",
                                Array[String]("id", sourceControlId,
                                    "progress-state", progress.state.toString.toLowerCase,
                                    "progress-received", progress.receivedSize.toString,
                                    "progress-expected", progress.expectedSize match {case Some(long) ⇒ long.toString; case _ ⇒ null}))
                            helper.endElement()
                            helper.endElement()

                        case _ ⇒
                    }
                }
            case _ ⇒ false
        }
    }

    // Process an incoming client event. Preprocessing for noscript and encrypted events is assumed to have taken place.
    // This handles checking for stale controls, relevance, readonly, and special cases like xf:output.
    // NOTE: Leave public for unit tests
    def processEvent(doc: XFormsContainingDocument, event: XFormsEvent) {

        // Check whether an event can be be dispatched to the given object. This only checks:
        // o the the target is still live
        // o that the target is not a non-relevant or readonly control
        def checkEventTarget(event: XFormsEvent): Boolean = {
            val eventTarget = event.targetObject
            val newReference = doc.getObjectByEffectiveId(eventTarget.getEffectiveId)

            def warn(condition: String) = {
                implicit val CurrentLogger = doc.getIndentedLogger
                debug("ignoring invalid client event on " + condition, Seq(
                    "control id" → eventTarget.getEffectiveId,
                    "event name" → event.name)
                )
                false
            }

            if (eventTarget ne newReference) {

                // Here, we check that the event's target is still a valid object. For example, a couple of events from the
                // UI could target controls. The first event is processed, which causes a change in the controls tree. The
                // second event would then refer to a control which no longer exist. In this case, we don't dispatch it.

                // We used to check simply by effective id, but this is not enough in some cases. We want to handle
                // controls that just "move" in a repeat. Scenario:
                //
                // o repeat with 2 iterations has xforms:input and xforms:trigger
                // o assume repeat is sorted on input value
                // o use changes value in input and clicks trigger
                // o client sends 2 events to server
                // o client processes value change and sets new value
                // o refresh takes place and causes reordering of rows
                // o client processes DOMActivate on trigger, which now has moved position, e.g. row 2 to row 1
                // o DOMActivate is dispatched to proper control (i.e. same as input was on)
                //
                // On the other hand, if the repeat iteration has disappeared, or was removed and recreated, the event is
                // not dispatched.

                warn("ghost target")

            } else eventTarget match {
                // Controls accept event only if they are relevant
                case control: XFormsControl if ! control.isRelevant ⇒
                    warn("non-relevant control")

                // Output control not subject to readonly condition below
                case control: XFormsOutputControl ⇒
                    true

                // Single node controls accept event only if they are not readonly
                case control: XFormsSingleNodeControl if control.isReadonly ⇒
                    warn("read-only control")

                // Single node controls accept value change event only if actually bound
                case control: XFormsSingleNodeControl if (control.getBoundItem eq null) && event.isInstanceOf[XXFormsValue] ⇒
                    warn("control without single-node binding")

                case _ ⇒
                    true
            }
        }

        def dispatchEventCheckTarget(event: XFormsEvent) =
            if (checkEventTarget(event))
                Dispatch.dispatchEvent(event)

        implicit val CurrentLogger = doc.getIndentedLogger(LOGGING_CATEGORY)
        val target = event.targetObject
        val targetEffectiveId = target.getEffectiveId
        val eventName = event.name

        withDebug("handling external event", Seq("target id" → targetEffectiveId, "event name" → eventName)) {

            // Optimize case where a value change event won't change the control value to actually change
            (event, target) match {
                case (valueChange: XXFormsValue, target: XFormsValueControl) if target.getExternalValue == valueChange.getNewValue ⇒
                    // We completely ignore the event if the value in the instance is the same. This also saves dispatching xxforms-repeat-activate below.
                    debug("ignoring value change event as value is the same", Seq(
                        "control id" → targetEffectiveId,
                        "event name" → eventName,
                        "value" → target.getExternalValue)
                    )
                    return
                case _ ⇒
            }

            // NOTES:

            // 1. We used to dispatch xforms-focus here, but now we don't anymore: we assume that the client provides
            //    xforms-focus before value changes as needed. Also, value changes can occur without focus changes, in
            //    particular when the JavaScript API is used.

            // 2. We also used to handle value controls here, but it makes more sense to do it via events.

            // 3. Recalculate, revalidate and refresh are handled with the automatic deferred updates.

            // 4. We used to do special handling for xforms:output: upon click on xforms:output, the client would send
            //    xforms-focus. We would translate that into DOMActivate. As of 2012-03-09 there doesn't seem to be a
            //    need for this so we are removing this behavior.

            // Each event is within its own start/end outermost action handler
            doc.startOutermostActionHandler()

            // Handle repeat iteration if the event target is in a repeat
            if (hasEffectiveIdSuffix(targetEffectiveId))
                dispatchEventCheckTarget(new XXFormsRepeatActivateEvent(doc, target))

            // Interpret event
            dispatchEventCheckTarget(event)
            doc.endOutermostActionHandler()
        }
    }
}