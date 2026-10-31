package io.github.omarmahamid.bbtree;

interface LeafSource {

	KeyOrder order();

	void loadLeaf(LeafWindow window, Object key, Routing routing);

}
