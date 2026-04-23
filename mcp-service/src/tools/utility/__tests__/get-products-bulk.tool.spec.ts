import { GetProductsBulkTool } from '../get-products-bulk.tool';
import { InventoryClient } from '../../../mcp/inventory.client';
import { MCP_BASE } from '../../constants';

describe('GetProductsBulkTool', () => {
  let tool: GetProductsBulkTool;
  let mockClient: Partial<InventoryClient>;

  beforeEach(() => {
    tool = new GetProductsBulkTool();
    mockClient = {
      post: jest.fn().mockResolvedValue([{ id: 1 }, { id: 2 }]),
    };
  });

  describe('schema validation', () => {
    it('should accept ids array', () => {
      const result = tool.schema.parse({ ids: [1, 2, 3] });
      expect(result).toHaveProperty('ids', [1, 2, 3]);
    });

    it('should accept codes array', () => {
      const result = tool.schema.parse({ codes: ['ABC', 'DEF'] });
      expect(result).toHaveProperty('codes', ['ABC', 'DEF']);
    });

    it('should accept empty object (both optional)', () => {
      expect(() => tool.schema.parse({})).not.toThrow();
    });
  });

  describe('execute', () => {
    it('should call client.post with correct path and body', async () => {
      await tool.execute(
        { ids: [1, 2], codes: [] },
        mockClient as InventoryClient,
        'jwt',
      );

      expect(mockClient.post).toHaveBeenCalledWith(
        `${MCP_BASE}/bulk/products`,
        expect.objectContaining({ ids: [1, 2] }),
        'jwt',
      );
    });
  });
});
