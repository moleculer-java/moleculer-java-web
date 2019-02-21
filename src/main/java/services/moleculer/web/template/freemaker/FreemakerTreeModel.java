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

import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNodeModel;
import freemarker.template.TemplateSequenceModel;
import io.datatree.Tree;

public class FreemakerTreeModel extends FreemakerAbstractModel implements TemplateNodeModel {

	// --- CONSTRUCTOR ---

	protected FreemakerTreeModel(Tree node) {
		super(node);
	}

	// --- NODE MODEL IMPLEMENTATION ---

	@Override
	public String getNodeNamespace() throws TemplateModelException {
		return null;
	}

	@Override
	public String getNodeName() throws TemplateModelException {
		return node.getName();
	}

	@Override
	public String getNodeType() throws TemplateModelException {
		return node.getName();
	}

	@Override
	public TemplateNodeModel getParentNode() throws TemplateModelException {
		Tree parent = node.getParent();
		return parent == null ? null : new FreemakerTreeModel(parent);
	}

	@Override
	public TemplateSequenceModel getChildNodes() throws TemplateModelException {
		return new FreemakerTreeSequenceModel(node);
	}

}