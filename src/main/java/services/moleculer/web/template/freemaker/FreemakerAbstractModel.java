/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2018 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.web.template.freemaker;

import java.util.Date;

import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;
import io.datatree.Tree;

public class FreemakerAbstractModel implements TemplateHashModel {

	// --- WRAPPED DATA STRUCTURE ---

	protected final Tree node;

	// --- CONSTRUCTOR ---

	protected FreemakerAbstractModel(Tree node) {
		this.node = node;
	}

	// --- HASH MODEL IMPLEMENTATION ---

	@Override
	public TemplateModel get(String key) throws TemplateModelException {
		return nodeToModel(node.get(key));
	}

	@Override
	public boolean isEmpty() throws TemplateModelException {
		return node.isEmpty();
	}

	// --- DATA CONVERTER ---

	protected static final TemplateModel nodeToModel(Tree node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isMap()) {
			return new FreemakerTreeModel(node);
		}
		if (node.isEnumeration()) {
			return new FreemakerTreeSequenceModel(node);
		}
		Object value = node.asObject();
		if (value instanceof Boolean) {
			return new TemplateBooleanModel() {

				@Override
				public final boolean getAsBoolean() {
					return (Boolean) value;
				}

			};
		}
		if (value instanceof Number) {
			return new TemplateNumberModel() {

				@Override
				public final Number getAsNumber() {
					return (Number) value;
				}

			};
		}
		if (value instanceof Date) {
			return new TemplateDateModel() {

				@Override
				public final int getDateType() {
					return DATETIME;
				}

				@Override
				public final Date getAsDate() {
					return (Date) value;
				}
			};
		}
		return new TemplateScalarModel() {

			@Override
			public final String getAsString() {
				return node.asString();
			}

		};
	}

}