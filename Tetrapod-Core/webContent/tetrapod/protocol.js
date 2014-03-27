// tetrapod protocol file, requires tetrapod main file to have been loaded first

TP.protocol = TP.protocol || { request: {}, response: {}, struct: {} };

TP.register("request", "BaseService", "Pause", 2, 14690004);
TP.register("request", "BaseService", "Unpause", 2, 10620319);
TP.register("request", "BaseService", "Shutdown", 2, 8989182);
TP.register("request", "BaseService", "Restart", 2, 4802943);
TP.register("struct", "Tetrapod", "Core", 1, 9088168);
TP.register("struct", "Tetrapod", "Handshake", 1, 7261648);
TP.register("struct", "Tetrapod", "RequestHeader", 1, 7165109);
TP.register("struct", "Tetrapod", "ResponseHeader", 1, 675609);
TP.register("struct", "Tetrapod", "MessageHeader", 1, 11760427);
TP.register("struct", "Tetrapod", "Entity", 1, 10171140);
TP.register("struct", "Tetrapod", "Subscriber", 1, 16013581);
TP.register("struct", "Tetrapod", "FlatTopic", 1, 3803415);
TP.register("struct", "Tetrapod", "WebRoute", 1, 4890284);
TP.register("struct", "Tetrapod", "TypeDescriptor", 1, 6493266);
TP.register("struct", "Tetrapod", "StructDescription", 1, 9642196);
TP.register("request", "Tetrapod", "Register", 1, 10895179);
TP.register("response", "Tetrapod", "Register", 1, 13376201);
TP.register("request", "Tetrapod", "Publish", 1, 3171651);
TP.register("response", "Tetrapod", "Publish", 1, 2698673);
TP.register("request", "Tetrapod", "RegistrySubscribe", 1, 2572089);
TP.register("request", "Tetrapod", "ServiceStatusUpdate", 1, 4487218);
TP.register("request", "Tetrapod", "AddWebRoutes", 1, 9719643);
TP.register("message", "Tetrapod", "EntityRegistered", 1, 1454035);
TP.register("message", "Tetrapod", "EntityUnregistered", 1, 14101566);
TP.register("message", "Tetrapod", "EntityUpdated", 1, 3775838);
TP.register("message", "Tetrapod", "TopicPublished", 1, 6873263);
TP.register("message", "Tetrapod", "TopicUnpublished", 1, 6594504);
TP.register("message", "Tetrapod", "TopicSubscribed", 1, 1498241);
TP.register("message", "Tetrapod", "TopicUnsubscribed", 1, 6934832);
TP.register("message", "Tetrapod", "ServiceAdded", 1, 15116807);
TP.register("message", "Tetrapod", "ServiceRemoved", 1, 1629937);
TP.register("message", "Tetrapod", "ServiceUpdated", 1, 1658756);
TP.register("message", "Tetrapod", "ServiceStats", 1, 469976);
TP.register("request", "Identity", "Login", 4, 8202985);
TP.register("response", "Identity", "Login", 4, 16389615);
TP.register("request", "Identity", "Create", 4, 6552804);
TP.register("response", "Identity", "Create", 4, 5348608);
TP.register("request", "Identity", "Info", 4, 14709500);
TP.register("response", "Identity", "Info", 4, 3624488);
TP.register("request", "Identity", "UpdateProperties", 4, 1362696);
TP.register("struct", "Identity", "User", 4, 10894876);
