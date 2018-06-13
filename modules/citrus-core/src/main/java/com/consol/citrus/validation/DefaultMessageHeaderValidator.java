/*
 * Copyright 2006-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.validation;

import com.consol.citrus.context.TestContext;
import com.consol.citrus.endpoint.resolver.DynamicEndpointUriResolver;
import com.consol.citrus.exceptions.ValidationException;
import com.consol.citrus.message.*;
import com.consol.citrus.validation.context.HeaderValidationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * Basic header message validator provides message header validation. Subclasses only have to add
 * specific logic for message payload validation. This validator is based on a control message.
 * 
 * @author Christoph Deppisch
 */
public class DefaultMessageHeaderValidator extends AbstractMessageValidator<HeaderValidationContext> {

    /** List of special header validators */
    @Autowired(required = false)
    private List<HeaderValidator> validators = new ArrayList<>();

    @Override
    public void validateMessage(Message receivedMessage, Message controlMessage, TestContext context, HeaderValidationContext validationContext) {
        Map<String, Object> controlHeaders = controlMessage.getHeaders();
        Map<String, Object> receivedHeaders = receivedMessage.getHeaders();

        if (CollectionUtils.isEmpty(controlHeaders)) { return; }

        log.debug("Start message header validation ...");

        for (Map.Entry<String, Object> entry : controlHeaders.entrySet()) {
            if (MessageHeaderUtils.isSpringInternalHeader(entry.getKey()) ||
                    entry.getKey().startsWith(MessageHeaders.MESSAGE_PREFIX) ||
                    entry.getKey().equals(DynamicEndpointUriResolver.ENDPOINT_URI_HEADER_NAME) ||
                    entry.getKey().equals(DynamicEndpointUriResolver.REQUEST_PATH_HEADER_NAME) ||
                    entry.getKey().equals(DynamicEndpointUriResolver.QUERY_PARAM_HEADER_NAME)) {
                continue;
            }

            final String headerName = getHeaderName(entry.getKey(), receivedHeaders, context, validationContext);

            if (!receivedHeaders.containsKey(headerName)) {
                throw new ValidationException("Validation failed: Header element '" + headerName + "' is missing");
            }

            Object controlValue = entry.getValue();
            validationContext.getValidators()
                    .stream()
                    .filter(validator -> validator.supports(headerName, Optional.ofNullable(controlValue).map(Object::getClass).orElse(null)))
                    .findFirst()
                    .orElse(
                        validationContext.getValidatorNames()
                                .stream()
                                .map(beanName -> {
                                    try {
                                        return context.getApplicationContext().getBean(beanName, HeaderValidator.class);
                                    } catch (NoSuchBeanDefinitionException e) {
                                        log.warn("Failed to resolve header validator for name: " + beanName);
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(
                                    validators.stream()
                                            .filter(validator -> validator.supports(headerName, Optional.ofNullable(controlValue).map(Object::getClass).orElse(null)))
                                            .findFirst()
                                            .orElse(new DefaultHeaderValidator())
                                )
                    ).validateHeader(headerName, receivedHeaders.get(headerName), controlValue, context, validationContext);
        }

        log.info("Message header validation successful: All values OK");
    }

    /**
     * Get header name from control message but also check if header expression is a variable or function. In addition to that find case insensitive header name in
     * received message when feature is activated.
     *
     * @param name
     * @param receivedHeaders
     * @param context
     * @param validationContext
     * @return
     */
    private String getHeaderName(String name, Map<String, Object> receivedHeaders, TestContext context, HeaderValidationContext validationContext) {
        String headerName = context.resolveDynamicValue(name);

        if (!receivedHeaders.containsKey(headerName) &&
                validationContext.isHeaderNameIgnoreCase()) {
            String key = headerName;

            log.debug(String.format("Finding case insensitive header for key '%s'", key));

            headerName = receivedHeaders
                    .entrySet()
                    .parallelStream()
                    .filter(item -> item.getKey().equalsIgnoreCase(key))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Validation failed: No matching header for key '" + key + "'"));

            log.info(String.format("Found matching case insensitive header name: %s", headerName));
        }

        return headerName;
    }

    @Override
    public boolean supportsMessageType(String messageType, Message message) {
        return true;
    }

    @Override
    protected Class<HeaderValidationContext> getRequiredValidationContextType() {
        return HeaderValidationContext.class;
    }

    /**
     * Adds header validator.
     * @param validator
     */
    public void addHeaderValidator(HeaderValidator validator) {
        this.validators.add(validator);
    }

    /**
     * Gets the validators.
     *
     * @return
     */
    public List<HeaderValidator> getValidators() {
        return Collections.unmodifiableList(validators);
    }

    /**
     * Sets the validators.
     *
     * @param validators
     */
    public void setValidators(List<HeaderValidator> validators) {
        this.validators = validators;
    }
}