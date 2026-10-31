package io.github.omarmahamid.bbtree;

enum Routing {

	COVERING,

	BELOW,

	LEFTMOST,

	RIGHTMOST;

	int childFor(InternalNode node, KeyOrder order, Object key) {
		return switch (this) {
			case COVERING -> node.route(order, key);
			case BELOW -> node.routeBelow(order, key);
			case LEFTMOST -> 0;
			case RIGHTMOST -> node.boundedSize();
		};
	}

}
