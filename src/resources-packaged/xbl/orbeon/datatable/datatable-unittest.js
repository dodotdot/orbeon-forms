/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * lib program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * lib program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */

(function() {
    var Assert = YAHOO.util.Assert,
        OD = ORBEON.util.Dom,
        Test = ORBEON.util.Test,
        UserAction = YAHOO.util.UserAction,
        YD = YAHOO.util.Dom,
        Document = ORBEON.xforms.Document;

    var testCase = {

        name: "datatable",

        testRepeatRefresh: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'repeat-refresh', function() {

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    // Setting maxLength to 0 deletes all the rows from the tables
                    ORBEON.xforms.Document.setValue("maxLength", "0");
                }, function() {
                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        // Setting maxLength to 100 adds new rows in the tables
                        ORBEON.xforms.Document.setValue("maxLength", "100");
                    }, function() {
                        // If the tables have been correctly updated, their cells styles (IE) or classes (FF) should be set
                        var table1 = YAHOO.util.Dom.get('my-accordion$repeat-refresh-table$repeat-refresh-table-table' + XFORMS_SEPARATOR_1 + '1');
                        var table2 = YAHOO.util.Dom.get('my-accordion$repeat-refresh-table$repeat-refresh-table-table' + XFORMS_SEPARATOR_1 + '2');
                        thiss.checkCellClasses(table1, true);
                        thiss.checkCellStyles(table1, true);
                        thiss.checkCellClasses(table2, true);
                        thiss.checkCellStyles(table2, true);

                        thiss.closeAccordionCase(thiss, 'repeat-refresh');

                    });
                });

            });
        }
        ,

        test314679: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314679', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314679$table-314679-table');
                thiss.clickAndCheckSortOrder(table, 1, 'descending', ['three', 'two', 'one'], function() {
                    thiss.closeAccordionCase(thiss, '_314679');
                });
            });
        }
        ,

        testOptionalScrollhV: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'optional-scrollh-v', function() {
                var tbody = YAHOO.util.Dom.get('my-accordion$optional-scrollh-v-table$optional-scrollh-v-table-tbody');
                var bodyContainer = tbody.parentNode.parentNode;
                thiss.checkHorizontalScrollbar(bodyContainer, false);
                thiss.closeAccordionCase(thiss, 'optional-scrollh-v');
            });
        }
        ,

        testOptionalScrollh: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'optional-scrollh', function() {
                var tbody = YAHOO.util.Dom.get('my-accordion$optional-scrollh-table$optional-scrollh-table-tbody');
                var bodyContainer = tbody.parentNode.parentNode;
                thiss.checkHorizontalScrollbar(bodyContainer, false);
                thiss.closeAccordionCase(thiss, 'optional-scrollh');
            });
        }
        ,

        test314466: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314466', function() {

                var table = YAHOO.util.Dom.get('my-accordion$_314466-table$_314466-table-table');
                var container = table.parentNode.parentNode;
                YAHOO.util.Assert.isFalse(YAHOO.util.Dom.hasClass(container, 'fr-dt-initialized'), "The datatable shouldn't be initialized at that point");
                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    YAHOO.util.UserAction.click(YAHOO.util.Dom.get('my-accordion$show-314466'), {clientX: 1});
                }, function() {
                    thiss.wait(function() {
                        table = YAHOO.util.Dom.get('my-accordion$_314466-table$_314466-table-table');
                        var container = table.parentNode.parentNode;
                        YAHOO.util.Assert.isTrue(YAHOO.util.Dom.hasClass(container, 'fr-dt-initialized'), "The datatable should be initialized at that point");

                        YAHOO.util.UserAction.click(YAHOO.util.Dom.get('my-accordion$hide-314466'), {clientX: 1});
                        thiss.closeAccordionCase(thiss, '_314466')
                    }, 200);

                });

            });
        },


        // Simple hide/show cycle
        test314459: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314459', function() {

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("maxLength", "30");
                    ORBEON.xforms.Document.setValue("show", "true");
                }, function() {
                    // Check the table structure
                    var table = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-table');
                    thiss.checkTableStructure(table, 1, false);
                    var container = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-container');
                    thiss.checkPaginationLinks(container, ['-<< first', '-< prev', '-1', '+2', '+next >', '+last >>']);

                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        ORBEON.xforms.Document.setValue("show", "false");
                    }, function() {
                        table = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-table');
                        thiss.checkTableStructure(table, 1, false);
                        container = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-container');
                        thiss.checkPaginationLinks(container, []);
                        ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                            ORBEON.xforms.Document.setValue("show", "true");
                        }, function() {
                            // Check the table structure
                            table = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-table');
                            thiss.checkTableStructure(table, 1, false);
                            container = YAHOO.util.Dom.get('my-accordion$_314459-table$_314459-table-container');
                            thiss.checkPaginationLinks(container, ['-<< first', '-< prev', '-1', '+2', '+next >', '+last >>']);

                            thiss.closeAccordionCase(thiss, '_314459');
                        });
                    });

                });

            });
        },

        test314415: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314415', function() {

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("show", "true");
                }, function() {
                    // Check the table structure
                    var table = YAHOO.util.Dom.get('my-accordion$_314415-table$_314415-table-table' + XFORMS_SEPARATOR_1 + '1');
                    thiss.checkTableStructure(table, 1, true);

                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        ORBEON.xforms.Document.setValue("show", "false");
                    }, function() {
                        table = YAHOO.util.Dom.get('my-accordion$_314415-table$_314415-table-table' + XFORMS_SEPARATOR_1 + '1');
                        YAHOO.util.Assert.isNull(table, "The table should have been deleted");
                        ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                            ORBEON.xforms.Document.setValue("show", "true");
                        }, function() {
                            // Check the table structure
                            table = YAHOO.util.Dom.get('my-accordion$_314415-table$_314415-table-table' + XFORMS_SEPARATOR_1 + '1');
                            thiss.checkTableStructure(table, 1, true);

                            thiss.closeAccordionCase(thiss, '_314415');
                        });
                    });

                });

            }
                    )
                    ;
        },


        test314422: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314422', function() {

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("maxLength", "1000");
                }, function() {
                    // Check the table structure
                    var table = YAHOO.util.Dom.get('my-accordion$_314422-table$_314422-table-table');
                    thiss.checkColumnValues(table, 1, false, [0, 1, 2, 3, 4]);
                    var container = YAHOO.util.Dom.get('my-accordion$_314422-table$_314422-table-container');
                    thiss.checkPaginationLinks(container, ['-<< first', '-< prev', '-1', '+2', '+3', '+4', '+5', '+6', '+7', '+8', '+next >', '+last >>']);

                    var link6 = thiss.getPaginateLink(container, 'last >>');
                    ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                        YAHOO.util.UserAction.click(link6, {clientX: 1});
                    }, function() {
                        thiss.checkColumnValues(table, 1, false, [35, 36, 37]);
                        thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '+2', '+3', '+4', '+5', '+6', '+7', '-8', '-next >', '-last >>']);
                        ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                            ORBEON.xforms.Document.setValue("maxLength", "30");
                        }, function() {
                            // Test the status after clicking on "last"
                            thiss.checkColumnValues(table, 1, false, [35, 37]);
                            thiss.checkPaginationLinks(container, ['+<< first', '+< prev', '+1', '-2', '-next >', '-last >>']);

                            thiss.closeAccordionCase(thiss, '_314422');
                        });
                    });
                });

            });
        }
        ,


        test314359: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314359', function() {
                // Check the table structure
                var table = YAHOO.util.Dom.get('my-accordion$table-314359$table-314359-table');
                thiss.checkIsSplit(table, true);
                thiss.checkTableStructure(table, 2, true);
                thiss.checkCellClasses(table, true);
                thiss.checkCellStyles(table, true);
                thiss.closeAccordionCase(thiss, '_314359');
            });

        }
        ,

        test314217: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314217', function() {
                var tbody = YAHOO.util.Dom.get('my-accordion$table-314217$table-314217-tbody');
                var bodyContainer = tbody.parentNode.parentNode;
                thiss.checkHorizontalScrollbar(bodyContainer);
                thiss.closeAccordionCase(thiss, '_314217');
            });
        }
        ,

        testWidths: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'widths', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-widths$table-widths-table');
                thiss.checkRowWidth(table.tHead.rows[0]);
                thiss.checkTableAndContainerWidths(YAHOO.util.Dom.get('my-accordion$table-widths$table-widths-table'));
                thiss.closeAccordionCase(thiss, 'widths');
            });
        }
        ,

        testWidthsResizeable: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
                thiss.checkRowWidth(table.tHead.rows[0]);
                thiss.checkTableAndContainerWidths(table);
                thiss.closeAccordionCase(thiss, 'widths-resizeable');
            });
        }
        ,

        testWidthsResizeable100pxRight: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
                var th1 = table.tHead.rows[0].cells[0];
                var th2 = table.tHead.rows[0].cells[1];
                var width1 = th1.clientWidth;
                var width2 = th2.clientWidth;
                thiss.resizeColumn(th2, 100, 10);
                thiss.checkTableAndContainerWidths(table);
                YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The width of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
                YAHOO.util.Assert.areEqual(width2 + 100, th2.clientWidth, "The width of the second column should be " + (width2 + 100) + ", not " + th2.clientWidth);
                thiss.checkRowWidth(table.tHead.rows[0]);
                thiss.closeAccordionCase(thiss, 'widths-resizeable');
            });
        }
        ,

        testWidthsResizeable50pxLeft: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
                var th1 = table.tHead.rows[0].cells[0];
                var th2 = table.tHead.rows[0].cells[1];
                var width1 = th1.clientWidth;
                var width2 = th2.clientWidth;
                thiss.resizeColumn(th2, -50, 10);
                thiss.checkTableAndContainerWidths(table);
                YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The width of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
                YAHOO.util.Assert.areEqual(width2 - 50, th2.clientWidth, "The width of the second column should be " + (width2 - 50) + ", not " + th2.clientWidth);
                thiss.checkRowWidth(table.tHead.rows[0]);
                thiss.closeAccordionCase(thiss, 'widths-resizeable');
            });
        }
        ,

        testWidthsResizeable10MorePxLeft: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, 'widths-resizeable', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-widths-resizeable$table-widths-resizeable-table');
                var th1 = table.tHead.rows[0].cells[0];
                var th2 = table.tHead.rows[0].cells[1];
                var width1 = th1.clientWidth;
                var width2 = th2.clientWidth;
                thiss.resizeColumn(th2, -10);
                thiss.checkTableAndContainerWidths(table);
                thiss.checkRowWidth(table.tHead.rows[0]);
                YAHOO.util.Assert.areEqual(width1, th1.clientWidth, "The wdith of the first column shouldn't change (before: " + width1 + ", after: " + width2 + ").");
                YAHOO.util.Assert.areEqual(width2 - 10, th2.clientWidth, "The width of the second column should be " + (width2 - 10) + ", not " + th2.clientWidth);
                thiss.closeAccordionCase(thiss, 'widths-resizeable');
            });
        }
        ,

        test314216: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314216', function() {
                var th = YAHOO.util.Dom.get('my-accordion$table-314216$th-314216-2');
                var resizerliner = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagAndClassName(th, 'div', 'yui-dt-resizerliner');
                var liner = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-liner');
                var resizer = YAHOO.xbl.fr.Datatable.utils.getFirstChildByTagAndClassName(resizerliner, 'div', 'yui-dt-resizer');
                thiss.resizeColumn(th, -100, 5);
                YAHOO.util.Assert.isTrue(th.clientWidth > 0, 'The column width should be greater than 0, not ' + th.clientWidth);
                thiss.checkTableAndContainerWidths(YAHOO.util.Dom.get('my-accordion$table-314216$table-314216-table'));
                thiss.checkCellWidth(th);
                thiss.closeAccordionCase(thiss, '_314216');
            });
        }
        ,

        test314209: function() {
            var thiss = this;
            thiss.closeAccordionCase(thiss, '_314209', function() {
                thiss.openAccordionCase(thiss, '_314209', function() {
                    var table = YAHOO.util.Dom.get('my-accordion$table-314209$table-314209-table');
                    var visibility = YAHOO.util.Dom.getStyle(table, 'visibility');
                    YAHOO.util.Assert.isTrue(visibility == 'visible' || visibility == 'inherit', 'Visibility should be visible or inherit, not ' + visibility);
                    // unfortunately, I haven't found any way to check that the table is actually visible!
                    thiss.closeAccordionCase(thiss, '_314209');
                });

            });
        }
        ,

        test314211: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314211', function() {
                var table = YAHOO.util.Dom.get('my-accordion$table-314211$table-314211-table');
                YAHOO.util.Assert.isTrue(table.clientWidth < 300, 'The table width (' + table.clientWidth + "px) should be small, let's say < 300px...");
                thiss.closeAccordionCase(thiss, '_314211');

            });
        }
        ,

        test314174: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314174', function() {
                //TODO: test something here!
                thiss.closeAccordionCase(thiss, '_314174');
            });
        }
        ,

        test314210: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314210', function() {
                var headerTable = YAHOO.util.Dom.get('my-accordion$table-314210$table-314210-table');
                YAHOO.util.Assert.isTrue(headerTable.clientWidth == headerTable.parentNode.clientWidth, 'The table header width (' + headerTable.clientWidth + 'px) should be equal than its container width (' + headerTable.parentNode.clientWidth + 'px).');
                thiss.closeAccordionCase(thiss, '_314210');
            });
        }
        ,

        test314292: function() {
            var thiss = this;
            thiss.openAccordionCase(thiss, '_314292', function() {
                window.resizeBy(-50, 0);
                thiss.wait(function() {
                    var table = YAHOO.util.Dom.get('my-accordion$table-314292$table-314292-table');
                    // The following test seems to detect the root cause of this bug
                    thiss.checkEmbeddedWidthAndHeight(table.parentNode.parentNode);
                    thiss.checkTableAndContainerWidths(table);
                    var tableX = YAHOO.util.Dom.getX(table);
                    var containerX = YAHOO.util.Dom.getX(table.parentNode.parentNode);
                    // The next one actually checks that the table does not overlap the border of the main container
                    // but is isn't 100% reliable
                    // YAHOO.util.Assert.areEqual(containerX, tableX - 1, 'The table left (' + tableX + ") should be 1 px right to the container left (" + containerX + ')');
                    window.resizeBy(50, 0);
                    thiss.closeAccordionCase(thiss, '_314292');
                }, 500);

            });
        }
        ,

        testFrGoToPageEvent: function() {
            this.openAccordionCase(this, "goto-page-event", function() {
                Test.executeCausingAjaxRequest(this, function() {
                    Test.click("my-accordion$goto-page-event-page-5");
                }, function() {
                    var testContainer = OD.get("my-accordion$goto-page-event");
                    var tbody = OD.getElementByTagName(testContainer, "tbody");
                    var outputSpan = OD.getElementByTagName(tbody, "span");
                    var firstRowText = Document.getValue(outputSpan.id);
                    Assert.areEqual("Integer quis metus vitae elit lobortis egestas.", firstRowText);
                    this.closeAccordionCase(this, "goto-page-event");
                });
            });
        },

        /**
         * Test with just sort-mode="external", i.e. sorting is handled by the user while paging is handled by
         * the datatable.
         */
        testInternalPagingExternalSorting: function() {
            this.openAccordionCase(this, "internal-paging-external-sorting", function() {
                var container = OD.get("my-accordion$internal-paging-external-sorting"),
                    table = YD.getElementsByClassName("yui-dt-table", "table", container)[0],
                    firstOutput  = YD.getElementsByClassName("xforms-output", "span", table)[0],
                    sortPosition = OD.getElementByTagName(YD.getElementsByClassName("yui-dt-label", "span", table)[0], "a"),
                    sortTextAnchor = OD.getElementByTagName(YD.getElementsByClassName("yui-dt-label", "span", table)[1], "a"),
                    nextAnchor = OD.getElementByTagName(YD.get("my-accordion$internal-paging-external-sorting-table$top-next-trigger"), "a"),
                    firstAnchor = OD.getElementByTagName(YD.get("my-accordion$internal-paging-external-sorting-table$top-first-trigger"), "a");
                Assert.areEqual("0", Document.getValue(firstOutput.id), "initially have 1");
                Test.executeSequenceCausingAjaxRequest(this, [[
                    // Test internal paging
                    function() { UserAction.click(nextAnchor); },
                    function() { Assert.areEqual("5", Document.getValue(firstOutput.id), "go to second page"); }
                ], [
                    // Back to first page
                    function() { UserAction.click(firstAnchor); },
                    function() { Assert.areEqual("0", Document.getValue(firstOutput.id), "back on first page"); }
                ], [
                    // Test external sorting
                    function() { UserAction.click(sortTextAnchor); },
                    function() { Assert.areEqual("33", Document.getValue(firstOutput.id), "sorting by text moved the row 33 to the beginning"); }
                ], [
                    // Do internal paging after sort
                    function() { UserAction.click(nextAnchor); },
                    function() { Assert.areEqual("7", Document.getValue(firstOutput.id), "second page sorted by text"); }
                ], [
                    // Back to sorting by position to restore instance in its original state
                    function() { UserAction.click(sortPosition); UserAction.click(firstAnchor); },
                    function() {
                        Assert.areEqual("0", Document.getValue(firstOutput.id), "back to initial state");
                        this.closeAccordionCase(this, "internal-paging-external-sorting");
                    }
                ]]);
            });
        },

        EOS: ""
    };

    ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
        for (var property in YAHOO.xbl.fr.Datatable.unittests_lib) {
            testCase[property] = YAHOO.xbl.fr.Datatable.unittests_lib[property];
        }
        YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase(testCase));
        AccordionMenu.setting('my-accordion$dl', {animation: true, seconds: 0.001, openedIds: [], dependent: false, easeOut: false});
        if (parent && parent.TestManager) {
            parent.TestManager.load();
        } else {
            new YAHOO.tool.TestLogger();
            YAHOO.tool.TestRunner.run();
        }
    });
})();